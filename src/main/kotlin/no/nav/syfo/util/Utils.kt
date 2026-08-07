package no.nav.syfo.util

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths


// Used as msgId-prefix for test messages from isyfomock
const val SYFO_MOCK_PREFIX = "syfomock-"

fun getFileAsString(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.UTF_8)

fun getFileAsStringISO88591(filePath: String) = String(Files.readAllBytes(Paths.get(filePath)), StandardCharsets.ISO_8859_1)
