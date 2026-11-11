package es.altamirano.linvideo.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class StorageTest {
    @Test fun emptyFolderFallsBackToDefault() {
        assertEquals("LinVideo", Storage.cleanFolder(""))
        assertEquals("LinVideo", Storage.cleanFolder("  /  "))
    }

    @Test fun folderCannotEscapeDownloads() {
        assertEquals("a/b", Storage.cleanFolder("../a/./b/.."))
        assertEquals("LinVideo", Storage.cleanFolder("../.."))
    }

    @Test fun nestedFoldersKeepTheirShape() {
        assertEquals("Vídeos/Música", Storage.cleanFolder("/Vídeos//Música/"))
    }

    @Test fun outputTemplateMatchesLinVideo() {
        assertEquals("%(title).150B [%(id)s].%(ext)s", Arguments.OUTPUT_TEMPLATE)
    }
}
