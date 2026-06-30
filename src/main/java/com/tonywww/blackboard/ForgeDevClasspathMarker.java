package com.tonywww.blackboard;

/**
 * Intentionally unused; has no runtime effect.
 *
 * <p>This is a Kotlin-only mod, so without any Java sources Gradle never populates
 * {@code build/classes/java/main}. Forge's dev-time {@code securejarhandler} builds a
 * {@code SecureJar} over the mod's source roots and probes {@code /META-INF/versions} for
 * multi-release handling; an <em>empty</em> output root makes its union filesystem throw
 * {@code UnionFileSystem$NoSuchFileException: /META-INF/versions}, crashing {@code runClient} /
 * {@code runServer} during mod discovery. A plain {@code package-info.java} does not emit a class
 * file, so this small package-private class is compiled instead to guarantee the directory exists
 * and is non-empty.
 */
final class ForgeDevClasspathMarker {
    private ForgeDevClasspathMarker() {
    }
}
