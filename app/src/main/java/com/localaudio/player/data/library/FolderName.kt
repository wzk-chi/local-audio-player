package com.localaudio.player.data.library

import android.net.Uri
import android.provider.DocumentsContract

internal fun fallbackFolderName(uri: Uri): String = runCatching {
    Uri.decode(DocumentsContract.getTreeDocumentId(uri).substringAfterLast(':'))
}.getOrDefault(uri.toString().substringAfterLast('/'))
