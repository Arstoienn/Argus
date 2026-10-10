package io.github.arstoienn.argus;

import static org.lwjgl.vulkan.KHRSurface.*;
import static org.lwjgl.vulkan.KHRSwapchain.*;
import static org.lwjgl.vulkan.VK10.*;

import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.Arrays;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.texture.GlTexture;
import net.vulkanmod.render.engine.VkGpuTexture;
import net.vulkanmod.render.engine.VkTextureView;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.device.DeviceManager;
import net.vulkanmod.vulkan.pass.MainPass;
import net.vulkanmod.vulkan.queue.Queue;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.lwjgl.PointerBuffer;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkAttachmentDescription;
import org.lwjgl.vulkan.VkAttachmentReference;
import org.lwjgl.vulkan.VkClearAttachment;
import org.lwjgl.vulkan.VkClearRect;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkCommandBufferAllocateInfo;
import org.lwjgl.vulkan.VkCommandBufferBeginInfo;
import org.lwjgl.vulkan.VkCommandPoolCreateInfo;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkFenceCreateInfo;
import org.lwjgl.vulkan.VkFramebufferCreateInfo;
import org.lwjgl.vulkan.VkImageBlit;
import org.lwjgl.vulkan.VkImageMemoryBarrier;
import org.lwjgl.vulkan.VkImageViewCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPresentInfoKHR;
import org.lwjgl.vulkan.VkRenderPassBeginInfo;
import org.lwjgl.vulkan.VkRenderPassCreateInfo;
import org.lwjgl.vulkan.VkSemaphoreCreateInfo;
import org.lwjgl.vulkan.VkSubmitInfo;
import org.lwjgl.vulkan.VkSubpassDependency;
import org.lwjgl.vulkan.VkSubpassDescription;
import org.lwjgl.vulkan.VkSurfaceCapabilitiesKHR;
import org.lwjgl.vulkan.VkSurfaceFormatKHR;
import org.lwjgl.vulkan.VkSwapchainCreateInfoKHR;

/**
 * How frames reach the second window when VulkanMod is installed.
 *
 * VulkanMod replaces the whole rendering backend, so the main window has no OpenGL context to
 * share and none of {@link SecondWindow}'s GL calls can run. The window gets a Vulkan surface and
 * swapchain of its own instead, and each frame is blitted into it on VulkanMod's device and queue.
 *
 * This class names net.vulkanmod and org.lwjgl.vulkan classes, which only exist with VulkanMod
 * installed. Nothing may touch it without checking {@link ArgusClient#VULKANMOD} first.
 */
public final class VulkanWindow {
    private static long window;
    private static long surface;
    private static boolean failed;

    private static long swapchain;
    private static long[] images = new long[0];
    private static int width;
    private static int height;
    private static boolean vsync;
    /** The swapchain no longer matches the surface and has to be made again. */
    private static boolean outOfDate = true;

    private static long commandPool;
    private static VkCommandBuffer commands;
    /** Signalled when the last blit has finished, so the command buffer and swapchain are free. */
    private static long blitDone;
    private static long imageAcquired;
    private static long readyToPresent;

    // The status text and the pointer are drawn in a render pass with only a colour attachment,
    // through one framebuffer for each swapchain image.
    private static long overlayPass;
    private static long[] imageViews = new long[0];
    private static long[] framebuffers = new long[0];
    private static final Overlay OVERLAY = new Overlay();

    private static Framebuffer pending;
    private static String pendingStatus = "";
    private static SecondView.Cursor pendingCursor;

    private static final SecondViewPass SECOND_VIEW_PASS = new SecondViewPass();

    private VulkanWindow() {
    }

    /** Gives a window created without a client API a surface and what is needed to present to it. */
    static void init(long handle) {
        window = handle;
        VkDevice device = Vulkan.getVkDevice();
        VkPhysicalDevice physicalDevice = DeviceManager.physicalDevice;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            LongBuffer out = stack.mallocLong(1);
            check(GLFWVulkan.glfwCreateWindowSurface(physicalDevice.getInstance(), handle, null, out), "glfwCreateWindowSurface");
            surface = out.get(0);
            IntBuffer supported = stack.mallocInt(1);
            check(vkGetPhysicalDeviceSurfaceSupportKHR(physicalDevice, Queue.getQueueFamilies().presentFamily, surface, supported), "surface support");
            if (supported.get(0) != VK_TRUE) {
                throw new IllegalStateException("VulkanMod's present queue cannot present to the second window");
            }

            VkCommandPoolCreateInfo poolInfo = VkCommandPoolCreateInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT)
                .queueFamilyIndex(Queue.getQueueFamilies().graphicsFamily);
            check(vkCreateCommandPool(device, poolInfo, null, out), "vkCreateCommandPool");
            commandPool = out.get(0);
            VkCommandBufferAllocateInfo bufferInfo = VkCommandBufferAllocateInfo.calloc(stack)
                .sType$Default()
                .commandPool(commandPool)
                .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY)
                .commandBufferCount(1);
            PointerBuffer buffer = stack.mallocPointer(1);
            check(vkAllocateCommandBuffers(device, bufferInfo, buffer), "vkAllocateCommandBuffers");
            commands = new VkCommandBuffer(buffer.get(0), device);

            // Created signalled, or the wait before the very first present would never return.
            VkFenceCreateInfo fenceInfo = VkFenceCreateInfo.calloc(stack).sType$Default().flags(VK_FENCE_CREATE_SIGNALED_BIT);
            check(vkCreateFence(device, fenceInfo, null, out), "vkCreateFence");
            blitDone = out.get(0);
            VkSemaphoreCreateInfo semaphoreInfo = VkSemaphoreCreateInfo.calloc(stack).sType$Default();
            check(vkCreateSemaphore(device, semaphoreInfo, null, out), "vkCreateSemaphore");
            imageAcquired = out.get(0);
            check(vkCreateSemaphore(device, semaphoreInfo, null, out), "vkCreateSemaphore");
            readyToPresent = out.get(0);
        }
    }

    // ---- The second pass ----

    /**
     * Called before the second pass draws, and paired with {@link #endSecondPass}.
     *
     * First the main view's commands recorded so far are submitted and waited for. On OpenGL every
     * draw happens as it is issued; VulkanMod only records them and executes the lot when the frame
     * ends, while the game's per-frame state exists once and is written once a frame. Whatever the
     * second pass writes over would be read by main-view draws that have not run yet, and their
     * entities, name tags and hitbox lines would come out with the second view's field of view and
     * fog. The price is that the CPU waits for the GPU to finish the main view before each pass.
     *
     * Then VulkanMod's main pass is swapped for one that binds the second view's framebuffer. Its
     * terrain and clouds do not go through the game's render passes: they start by rebinding the
     * "main target", which would be the main window's swapchain.
     */
    static void beginSecondPass() {
        Renderer renderer = Renderer.getInstance();
        renderer.flushCmds();
        if (renderer.getMainPass() != SECOND_VIEW_PASS) {
            SECOND_VIEW_PASS.original = renderer.getMainPass();
            renderer.setMainPass(SECOND_VIEW_PASS);
        }
    }

    static void endSecondPass() {
        Renderer renderer = Renderer.getInstance();
        if (renderer.getMainPass() == SECOND_VIEW_PASS) {
            renderer.setMainPass(SECOND_VIEW_PASS.original);
        }
    }

    /** VulkanMod's main pass with the main target replaced by the second view's framebuffer. */
    private static final class SecondViewPass implements MainPass {
        private MainPass original;

        @Override
        public void rebindMainTarget() {
            Framebuffer target = SecondView.redirect;
            if (target != null && target.getColorAttachmentView() instanceof VkTextureView view) {
                view.getFbo(target.getDepthAttachment()).bind();
                // Binding an offscreen framebuffer leaves VulkanMod's viewport flipped, the way a GL
                // texture is. Its own render passes set it back straight away; terrain and clouds
                // do not, and would be drawn upside down with their faces culled.
                GlStateManager._viewport(0, 0, target.textureWidth, target.textureHeight);
            } else {
                this.original.rebindMainTarget();
            }
        }

        @Override
        public void begin(VkCommandBuffer commandBuffer, MemoryStack stack) {
            this.original.begin(commandBuffer, stack);
        }

        @Override
        public void end(VkCommandBuffer commandBuffer) {
            this.original.end(commandBuffer);
        }

        @Override
        public void cleanUp() {
            this.original.cleanUp();
        }

        @Override
        public void onResize() {
            this.original.onResize();
        }

        @Override
        public void mainTargetBindWrite() {
            this.original.mainTargetBindWrite();
        }

        @Override
        public void mainTargetUnbindWrite() {
            this.original.mainTargetUnbindWrite();
        }

        @Override
        public void bindAsTexture() {
            this.original.bindAsTexture();
        }

        @Override
        public net.vulkanmod.vulkan.framebuffer.Framebuffer getMainFramebuffer() {
            return this.original.getMainFramebuffer();
        }

        @Override
        public GpuTexture getColorAttachment() {
            return this.original.getColorAttachment();
        }

        @Override
        public GpuTextureView getColorAttachmentView() {
            return this.original.getColorAttachmentView();
        }

        @Override
        public GpuTexture getDepthAttachment() {
            return this.original.getDepthAttachment();
        }
    }

    // ---- Presenting ----

    /** Sets what this frame shows. It reaches the window in {@link #presentPending}. */
    static void queue(Framebuffer source, String status, SecondView.Cursor cursor) {
        pending = source;
        pendingStatus = status;
        pendingCursor = cursor;
    }

    /**
     * Called once VulkanMod has submitted and presented the main frame.
     *
     * The second view cannot be blitted where the second pass ends: its draws have only been
     * recorded by then. This uses a command buffer of its own, submitted after the main frame's.
     * A queue executes in the order of submission, so the blit reads the finished picture, and the
     * layout VulkanMod has on record for the image, which follows recording order, is by now the
     * layout it is really in.
     */
    public static void presentPending() {
        Framebuffer source = pending;
        pending = null;
        if (source == null || failed || surface == 0L) {
            return;
        }
        try {
            present(source);
        } catch (RuntimeException e) {
            failed = true;
            ArgusClient.LOGGER.error("[argus] Vulkan presentation failed — second window disabled", e);
        }
    }

    private static void present(Framebuffer source) {
        VulkanImage image = image(source.getColorAttachment());
        if (image == null) {
            return;
        }
        VkDevice device = Vulkan.getVkDevice();
        vkWaitForFences(device, blitDone, true, -1L);

        int[] windowWidth = new int[1];
        int[] windowHeight = new int[1];
        GLFW.glfwGetFramebufferSize(window, windowWidth, windowHeight);
        if (windowWidth[0] <= 0 || windowHeight[0] <= 0) {
            return;
        }
        boolean wantVsync = ArgusConfig.get().secondWindowVsync;
        boolean stale = swapchain == 0L || outOfDate || windowWidth[0] != width || windowHeight[0] != height || wantVsync != vsync;
        if (stale && !createSwapchain(wantVsync)) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer acquired = stack.mallocInt(1);
            int result = vkAcquireNextImageKHR(device, swapchain, -1L, imageAcquired, VK_NULL_HANDLE, acquired);
            if (result == VK_ERROR_OUT_OF_DATE_KHR) {
                outOfDate = true;
                return;
            }
            if (result != VK_SUBOPTIMAL_KHR) {
                check(result, "vkAcquireNextImageKHR");
            }
            int index = acquired.get(0);
            long target = images[index];

            vkResetFences(device, blitDone);
            vkResetCommandBuffer(commands, 0);
            VkCommandBufferBeginInfo beginInfo = VkCommandBufferBeginInfo.calloc(stack)
                .sType$Default()
                .flags(VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT);
            check(vkBeginCommandBuffer(commands, beginInfo), "vkBeginCommandBuffer");

            int sourceLayout = image.getCurrentLayout();
            image.transitionImageLayout(stack, commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
            // The source stage is TRANSFER because that is the stage the submit waits for the
            // acquire semaphore at, which puts this transition after the image is really ours.
            transition(stack, target, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 0, VK_ACCESS_TRANSFER_WRITE_BIT,
                VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            VkImageBlit.Buffer region = VkImageBlit.calloc(1, stack);
            region.srcSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.srcOffsets(0).set(0, 0, 0);
            region.srcOffsets(1).set(image.width, image.height, 1);
            region.dstSubresource().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).layerCount(1);
            region.dstOffsets(0).set(0, 0, 0);
            region.dstOffsets(1).set(width, height, 1);
            vkCmdBlitImage(commands, image.getId(), VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, target, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, region, VK_FILTER_LINEAR);

            OVERLAY.clear();
            BitmapText.paintStatus(OVERLAY, pendingStatus, width, height);
            if (pendingCursor != null) {
                // Placed the way SecondWindow places it: a fraction of the main window, origin bottom left.
                int x = (int) Math.round(pendingCursor.x() * width);
                int y = (int) Math.round((1.0 - pendingCursor.y()) * height);
                BitmapText.paintCursor(OVERLAY, x, y, Math.max(1, Math.round(height / 700.0F)));
            }
            if (OVERLAY.isEmpty()) {
                transition(stack, target, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR, VK_ACCESS_TRANSFER_WRITE_BIT, 0,
                    VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);
            } else {
                // The render pass does the layouts itself: in as TRANSFER_DST, out as PRESENT_SRC.
                VkRenderPassBeginInfo passInfo = VkRenderPassBeginInfo.calloc(stack)
                    .sType$Default()
                    .renderPass(overlayPass)
                    .framebuffer(framebuffers[index]);
                passInfo.renderArea().extent().set(width, height);
                vkCmdBeginRenderPass(commands, passInfo, VK_SUBPASS_CONTENTS_INLINE);
                OVERLAY.record(stack);
                vkCmdEndRenderPass(commands);
            }
            if (sourceLayout != VK_IMAGE_LAYOUT_UNDEFINED) {
                // Hand the image back in the layout VulkanMod has on record for it.
                image.transitionImageLayout(stack, commands, sourceLayout);
            }
            check(vkEndCommandBuffer(commands), "vkEndCommandBuffer");

            VkSubmitInfo submit = VkSubmitInfo.calloc(stack)
                .sType$Default()
                .waitSemaphoreCount(1)
                .pWaitSemaphores(stack.longs(imageAcquired))
                .pWaitDstStageMask(stack.ints(VK_PIPELINE_STAGE_TRANSFER_BIT))
                .pCommandBuffers(stack.pointers(commands))
                .pSignalSemaphores(stack.longs(readyToPresent));
            Queue graphics = DeviceManager.getGraphicsQueue();
            // VulkanMod's own submits are synchronized on the queue object; share that lock.
            synchronized (graphics) {
                check(vkQueueSubmit(graphics.vkQueue(), submit, blitDone), "vkQueueSubmit");
            }

            VkPresentInfoKHR presentInfo = VkPresentInfoKHR.calloc(stack)
                .sType$Default()
                .pWaitSemaphores(stack.longs(readyToPresent))
                .swapchainCount(1)
                .pSwapchains(stack.longs(swapchain))
                .pImageIndices(stack.ints(index));
            result = vkQueuePresentKHR(DeviceManager.getPresentQueue().vkQueue(), presentInfo);
            if (result == VK_ERROR_OUT_OF_DATE_KHR || result == VK_SUBOPTIMAL_KHR) {
                outOfDate = true;
            } else {
                check(result, "vkQueuePresentKHR");
            }
        }
    }

    private static VulkanImage image(GpuTexture texture) {
        if (texture instanceof VkGpuTexture vkTexture) {
            return vkTexture.getVulkanImage();
        }
        return texture instanceof GlTexture glTexture ? VkGpuTexture.fromGlTexture(glTexture).getVulkanImage() : null;
    }

    // ---- The swapchain ----

    /** Makes the swapchain for the window's current size. False while the window has no area. */
    private static boolean createSwapchain(boolean wantVsync) {
        VkDevice device = Vulkan.getVkDevice();
        VkPhysicalDevice physicalDevice = DeviceManager.physicalDevice;

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkSurfaceCapabilitiesKHR capabilities = VkSurfaceCapabilitiesKHR.malloc(stack);
            check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(physicalDevice, surface, capabilities), "surface capabilities");
            int newWidth = capabilities.currentExtent().width();
            int newHeight = capabilities.currentExtent().height();
            if (newWidth == -1) {
                int[] windowWidth = new int[1];
                int[] windowHeight = new int[1];
                GLFW.glfwGetFramebufferSize(window, windowWidth, windowHeight);
                newWidth = Math.clamp(windowWidth[0], capabilities.minImageExtent().width(), capabilities.maxImageExtent().width());
                newHeight = Math.clamp(windowHeight[0], capabilities.minImageExtent().height(), capabilities.maxImageExtent().height());
            }
            if (newWidth <= 0 || newHeight <= 0) {
                return false;
            }

            IntBuffer count = stack.mallocInt(1);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, null), "surface formats");
            VkSurfaceFormatKHR.Buffer formats = VkSurfaceFormatKHR.malloc(count.get(0), stack);
            check(vkGetPhysicalDeviceSurfaceFormatsKHR(physicalDevice, surface, count, formats), "surface formats");
            // The source is UNORM, so the target should be too. Into an SRGB format the blit would
            // gamma-encode the picture a second time and it would come out too bright.
            int format = formats.get(0).format();
            int colorSpace = formats.get(0).colorSpace();
            for (int i = 0; i < formats.capacity(); i++) {
                int candidate = formats.get(i).format();
                boolean unorm = candidate == VK_FORMAT_B8G8R8A8_UNORM || candidate == VK_FORMAT_R8G8B8A8_UNORM;
                if (unorm && formats.get(i).colorSpace() == VK_COLOR_SPACE_SRGB_NONLINEAR_KHR) {
                    format = candidate;
                    colorSpace = formats.get(i).colorSpace();
                    break;
                }
            }

            check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, null), "present modes");
            IntBuffer modes = stack.mallocInt(count.get(0));
            check(vkGetPhysicalDeviceSurfacePresentModesKHR(physicalDevice, surface, count, modes), "present modes");
            // FIFO always exists. Without vsync prefer IMMEDIATE, then MAILBOX: neither makes the
            // render thread wait for the second window's vblank.
            int presentMode = VK_PRESENT_MODE_FIFO_KHR;
            if (!wantVsync) {
                boolean immediate = false;
                boolean mailbox = false;
                for (int i = 0; i < modes.capacity(); i++) {
                    immediate |= modes.get(i) == VK_PRESENT_MODE_IMMEDIATE_KHR;
                    mailbox |= modes.get(i) == VK_PRESENT_MODE_MAILBOX_KHR;
                }
                presentMode = immediate ? VK_PRESENT_MODE_IMMEDIATE_KHR : mailbox ? VK_PRESENT_MODE_MAILBOX_KHR : VK_PRESENT_MODE_FIFO_KHR;
            }

            int imageCount = capabilities.minImageCount() + 1;
            if (capabilities.maxImageCount() > 0) {
                imageCount = Math.min(imageCount, capabilities.maxImageCount());
            }

            destroyOverlayTargets();
            long previous = swapchain;
            VkSwapchainCreateInfoKHR info = VkSwapchainCreateInfoKHR.calloc(stack)
                .sType$Default()
                .surface(surface)
                .minImageCount(imageCount)
                .imageFormat(format)
                .imageColorSpace(colorSpace)
                .imageArrayLayers(1)
                .imageUsage(VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT)
                .imageSharingMode(VK_SHARING_MODE_EXCLUSIVE)
                .preTransform(capabilities.currentTransform())
                .compositeAlpha(VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR)
                .presentMode(presentMode)
                .clipped(true)
                .oldSwapchain(previous);
            info.imageExtent().set(newWidth, newHeight);
            LongBuffer out = stack.mallocLong(1);
            check(vkCreateSwapchainKHR(device, info, null, out), "vkCreateSwapchainKHR");
            swapchain = out.get(0);
            if (previous != 0L) {
                vkDestroySwapchainKHR(device, previous, null);
            }

            check(vkGetSwapchainImagesKHR(device, swapchain, count, null), "swapchain images");
            LongBuffer handles = stack.mallocLong(count.get(0));
            check(vkGetSwapchainImagesKHR(device, swapchain, count, handles), "swapchain images");
            images = new long[count.get(0)];
            handles.get(images);
            createOverlayTargets(stack, format, newWidth, newHeight);
            width = newWidth;
            height = newHeight;
            vsync = wantVsync;
            outOfDate = false;
            return true;
        }
    }

    private static void createOverlayTargets(MemoryStack stack, int format, int targetWidth, int targetHeight) {
        VkDevice device = Vulkan.getVkDevice();
        LongBuffer out = stack.mallocLong(1);
        VkAttachmentDescription.Buffer attachment = VkAttachmentDescription.calloc(1, stack)
            .format(format)
            .samples(VK_SAMPLE_COUNT_1_BIT)
            .loadOp(VK_ATTACHMENT_LOAD_OP_LOAD)
            .storeOp(VK_ATTACHMENT_STORE_OP_STORE)
            .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE)
            .stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
            .initialLayout(VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL)
            .finalLayout(VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        VkAttachmentReference.Buffer reference = VkAttachmentReference.calloc(1, stack)
            .attachment(0)
            .layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        VkSubpassDescription.Buffer subpass = VkSubpassDescription.calloc(1, stack)
            .pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
            .colorAttachmentCount(1)
            .pColorAttachments(reference);
        // The blit before it writes the image as a transfer; the subpass has to wait for that.
        VkSubpassDependency.Buffer dependency = VkSubpassDependency.calloc(1, stack)
            .srcSubpass(VK_SUBPASS_EXTERNAL)
            .dstSubpass(0)
            .srcStageMask(VK_PIPELINE_STAGE_TRANSFER_BIT)
            .dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT)
            .srcAccessMask(VK_ACCESS_TRANSFER_WRITE_BIT)
            .dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT);
        VkRenderPassCreateInfo passInfo = VkRenderPassCreateInfo.calloc(stack)
            .sType$Default()
            .pAttachments(attachment)
            .pSubpasses(subpass)
            .pDependencies(dependency);
        check(vkCreateRenderPass(device, passInfo, null, out), "vkCreateRenderPass");
        overlayPass = out.get(0);

        imageViews = new long[images.length];
        framebuffers = new long[images.length];
        for (int i = 0; i < images.length; i++) {
            VkImageViewCreateInfo viewInfo = VkImageViewCreateInfo.calloc(stack)
                .sType$Default()
                .image(images[i])
                .viewType(VK_IMAGE_VIEW_TYPE_2D)
                .format(format);
            viewInfo.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
            check(vkCreateImageView(device, viewInfo, null, out), "vkCreateImageView");
            imageViews[i] = out.get(0);
            VkFramebufferCreateInfo framebufferInfo = VkFramebufferCreateInfo.calloc(stack)
                .sType$Default()
                .renderPass(overlayPass)
                .pAttachments(stack.longs(imageViews[i]))
                .width(targetWidth)
                .height(targetHeight)
                .layers(1);
            check(vkCreateFramebuffer(device, framebufferInfo, null, out), "vkCreateFramebuffer");
            framebuffers[i] = out.get(0);
        }
    }

    private static void destroyOverlayTargets() {
        VkDevice device = Vulkan.getVkDevice();
        for (long framebuffer : framebuffers) {
            vkDestroyFramebuffer(device, framebuffer, null);
        }
        for (long view : imageViews) {
            vkDestroyImageView(device, view, null);
        }
        if (overlayPass != 0L) {
            vkDestroyRenderPass(device, overlayPass, null);
            overlayPass = 0L;
        }
        framebuffers = new long[0];
        imageViews = new long[0];
    }

    /**
     * Collects the cells the status text and pointer are made of, then clears each to its colour
     * with vkCmdClearAttachments. That needs no pipeline and no shader, like the scissored clears
     * {@link BitmapText} does on OpenGL.
     */
    private static final class Overlay implements BitmapText.Painter {
        private static final int MAX_COLORS = 16;

        /** Four ints a cell: x, y (GL's, origin bottom left), width, height. */
        private int[] cells = new int[4096];
        private int count;
        // Each change of colour starts a run: the cell it starts at, and its red, green and blue.
        private final int[] runStart = new int[MAX_COLORS];
        private final float[] runColor = new float[MAX_COLORS * 3];
        private int runs;

        void clear() {
            this.count = 0;
            this.runs = 0;
        }

        boolean isEmpty() {
            return this.count == 0;
        }

        @Override
        public void color(float r, float g, float b) {
            if (this.runs == MAX_COLORS) {
                return;
            }
            this.runStart[this.runs] = this.count;
            this.runColor[this.runs * 3] = r;
            this.runColor[this.runs * 3 + 1] = g;
            this.runColor[this.runs * 3 + 2] = b;
            this.runs++;
        }

        @Override
        public void cell(int x, int y, int size) {
            if (this.runs == 0) {
                return;
            }
            if (this.count * 4 == this.cells.length) {
                this.cells = Arrays.copyOf(this.cells, this.cells.length * 2);
            }
            this.cells[this.count * 4] = x;
            this.cells[this.count * 4 + 1] = y;
            this.cells[this.count * 4 + 2] = size;
            this.cells[this.count * 4 + 3] = size;
            this.count++;
        }

        void record(MemoryStack stack) {
            for (int run = 0; run < this.runs; run++) {
                int first = this.runStart[run];
                int length = (run + 1 < this.runs ? this.runStart[run + 1] : this.count) - first;
                if (length <= 0) {
                    continue;
                }
                // A run can be more than a thousand cells, too many for the stack.
                VkClearRect.Buffer rects = VkClearRect.calloc(length);
                int used = 0;
                for (int i = 0; i < length; i++) {
                    int at = (first + i) * 4;
                    // A rectangle outside the image is not valid for vkCmdClearAttachments; clip it.
                    int left = Math.max(this.cells[at], 0);
                    int top = Math.max(height - (this.cells[at + 1] + this.cells[at + 3]), 0);
                    int rectWidth = Math.min(this.cells[at] + this.cells[at + 2], width) - left;
                    int rectHeight = Math.min(height - this.cells[at + 1], height) - top;
                    if (rectWidth > 0 && rectHeight > 0) {
                        VkClearRect rect = rects.get(used++);
                        rect.rect().offset().set(left, top);
                        rect.rect().extent().set(rectWidth, rectHeight);
                        rect.baseArrayLayer(0).layerCount(1);
                    }
                }
                if (used > 0) {
                    rects.limit(used);
                    VkClearAttachment.Buffer attachment = VkClearAttachment.calloc(1, stack)
                        .aspectMask(VK_IMAGE_ASPECT_COLOR_BIT)
                        .colorAttachment(0);
                    attachment.clearValue().color()
                        .float32(0, this.runColor[run * 3])
                        .float32(1, this.runColor[run * 3 + 1])
                        .float32(2, this.runColor[run * 3 + 2])
                        .float32(3, 1.0F);
                    vkCmdClearAttachments(commands, attachment, rects);
                }
                rects.free();
            }
        }
    }

    private static void transition(MemoryStack stack, long image, int from, int to, int sourceAccess, int targetAccess, int sourceStage, int targetStage) {
        VkImageMemoryBarrier.Buffer barrier = VkImageMemoryBarrier.calloc(1, stack)
            .sType$Default()
            .oldLayout(from)
            .newLayout(to)
            .srcAccessMask(sourceAccess)
            .dstAccessMask(targetAccess)
            .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
            .image(image);
        barrier.subresourceRange().aspectMask(VK_IMAGE_ASPECT_COLOR_BIT).levelCount(1).layerCount(1);
        vkCmdPipelineBarrier(commands, sourceStage, targetStage, 0, null, null, barrier);
    }

    private static void check(int result, String what) {
        if (result != VK_SUCCESS) {
            throw new IllegalStateException(what + " failed: VkResult " + result);
        }
    }
}
