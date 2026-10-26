package com.nuvio.app.core.share

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.UIKit.popoverPresentationController

internal actual object PlatformShare {
    @OptIn(ExperimentalForeignApi::class)
    actual fun shareText(text: String) {
        val presenter = topViewController() ?: return
        val controller = UIActivityViewController(activityItems = listOf(text), applicationActivities = null)
        // iPad: a folha abre como popover e precisa de uma âncora.
        controller.popoverPresentationController?.let { popover ->
            val view = presenter.view
            popover.sourceView = view
            view.bounds.useContents {
                popover.sourceRect = CGRectMake(size.width / 2.0, size.height / 2.0, 1.0, 1.0)
            }
        }
        presenter.presentViewController(controller, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    var controller = UIApplication.sharedApplication.keyWindow?.rootViewController
    while (controller?.presentedViewController != null) {
        controller = controller.presentedViewController
    }
    return controller
}
