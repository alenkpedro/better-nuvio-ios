package com.nuvio.app.core.storage

/**
 * Better Nuvio: texto por perfil para os recursos portados da TV que guardam um
 * arquivinho por perfil (fila-pN.txt, tempo-dia-pN.txt). NSUserDefaults no iOS e
 * SharedPreferences no Android, como os outros repositórios por perfil.
 */
internal expect object ProfileTextStorage {
    fun load(name: String, profileId: Int): String?
    fun save(name: String, profileId: Int, payload: String)
}
