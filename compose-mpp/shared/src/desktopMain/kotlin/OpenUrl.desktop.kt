actual fun openUrl(url: String): Boolean {
    return try {
        val desktop = java.awt.Desktop.getDesktop()
        desktop.browse(java.net.URI(url))
        true
    } catch (e: Exception) {
        false
    }
}