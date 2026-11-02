# Essential

Essential is a quality of life mod that boosts Minecraft Java Edition to the next level!

The source code of the Essential Mod is accessible to everyone, demonstrating our commitment to transparency with our
users and the broader community.

For assistance with this repository or the mod, please utilize the dedicated channels available in our [Discord](https://essential.gg/discord).

Discover more about Essential on our [Website](https://essential.gg) or visit the [Essential Wiki](https://essential.gg/wiki).

## Building

Before building Essential, you must have [Java Development Kits (JDKs)](https://adoptium.net/temurin/releases/)
installed for Java versions 21, 17, 16, and 8 (even if you only want to build for a specific Minecraft version).
Java 21 (or newer) must be the default Java version on your system.

No additional tools are required. Gradle will be automatically installed by the
[gradle-wrapper](https://docs.gradle.org/current/userguide/gradle_wrapper.html) program included in the repository and
available via the `./gradlew` (Linux/Mac) or `gradlew.bat` (Windows) scripts.
We highly recommend using these instead of a local installation of Gradle to ensure you're using the exact same version
of Gradle as was used for the official builds. We cannot guarantee that older or newer versions will work and produce
bit-for-bit identical output.

Note that this repository uses [git submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules).
Be sure to run `git submodule update --init --recursive` after cloning the repository for the first time (or clone with
`--recursive`), and also every time after you pull a new version.

### Building Essential Mod

To build all of Essential for all Minecraft versions, run `./gradlew build`.
Depending on your system and internet connection the first build may take anywhere from 10 minutes to an hour.

To build for a specific Minecraft version, run `./gradlew :<version>-<loader>:build`, e.g. for Minecraft 1.12.2 run
`./gradlew :1.12.2-forge:build`.
Note that building any version other than the main version (currently 1.12.2) will require all versions between it and
the main version to be set up regardless, so the time saved over building for all versions may vary wildly.

Once finished, you should be able to find the Essential jars in `versions/<MC-Version>/build/libs/`.

The jar file starting with `pinned_` is the mod file made available via Modrinth/CurseForge.
The other jar file is downloaded by the in-game update functionality, third-party mods which embed the
Essential Loader, the thin container mods available on essential.gg/download, as well as the Essential Installer.

### Building Essential Loader

The latest version of Essential Loader is automatically built when building [Essential](#building-essential-mod) because
it is included in the `pinned` jar files.

The loader is split into three "stages" (for details see `loader/docs/stages.md`) each with one jar per "platform"
(for details see `loader/docs/platforms.md`).
You can find these jar files in `loader/<stage>/<platform>/build/libs/`.

### Building Essential Container

The Essential Container is a thin mod which does nothing but download Essential on first launch.
The jar files available for download on essential.gg/download and installed by the Essential Installer are such
"Container" files.
Unlike the jars published on Modrinth/CurseForge ("pinned" jars), it does not contain a specific version of Essential,
rather it downloads whatever version is the latest at first launch.

Given it only contains code to load Essential and no code to directly interact with Minecraft itself, there are only
four different versions:
- `fabric` for everything Fabric
- `launchwrapper` for Forge on Minecraft 1.8.9 and 1.12.2
- `modlauncher8` for Forge on Minecraft 1.16.5
- `modlauncher9` for Forge on Minecraft 1.17 and above
- `fml10` for NeoForge on Minecraft 1.21.11 and above

For more technical details about these different platforms, see `loader/docs/platforms.md`.
For more technical details about "container"/"pinned" mods, see `loader/docs/container-mods.md`.

To build the Essential Container, run `./gradlew :loader:container:<platform>:build` where `<platform>` is one of the
four platforms listed above.
You can find the resulting jar file in `loader/container/<platform>/build/libs/`.

## CI

Every Essential release is built by CI twice, once by our main (internal, self-hosted) CI system and a second time
directly from this repository on a GitHub-provided runner.

The first internal run is generally much faster and includes a few extra steps such as integration tests, uploading
(but not yet publishing) of the jars to our infrastructure, as well as publishing the source code to this repository.

The second run, directly from this repository, exists primarily to ensure that the source code we publish actually
matches the jars that were produced and uploaded by the first run.
After building the mod from scratch directly from publicly accessible source code in the GitHub-provided clean
environment, it will download the main jars from our infrastructure and ensure that they are bit-for-bit identical to
the ones it just built.

It will also log and make available as an artifact via GitHub the checksums of the files it built, such that
third-parties may independently verify the files served by our infrastructure without having to build the entire mod
themselves.
Note that GitHub will however unfortunately delete Actions logs and artifacts after some time.

It will not verify the checksums of the "pinned" jars (those available via Modrinth/CurseForge) because these are
deterministically derived from the main jars (see `build-logic/src/main/kotlin/essential/pinned-jar.gradle.kts`), so
verifying the main jars is sufficient.
Our internal CI does not even upload these pinned jars, they are re-generated on demand when publishing to
Modrinth/CurseForge.
Their checksums are printed to the publicly visible log during the second run though, so third-parties may at any time
compare them to the files served by Modrinth/CurseForge.

## Verifying checksums

To verify checksums of any Essential-related files from your `.minecraft` folder, first either
[build](#building-essential-mod) the respective Essential version, or find the corresponding GitHub Actions run and
download its `checksums` text file / look at the `Generate checksums` log section of its `build` job.

Then use the below sub-sections to identify which kind of file you are looking at, as well as what path to find the
respective jar file at in this repository.

If you have built Essential yourself, you may then compare the file at the given path to the one in your `.minecraft`
folder.
If you are looking at the GitHub Actions run, you are looking at a list of files with their corresponding
[SHA-256 checksum](https://en.wikipedia.org/wiki/SHA-2). Use a program (e.g. `sha256sum` on Linux) to generate the
checksum of the file in your `.minecraft` folder and compare it to the checksum of the file in the list.

Note that some Essential versions are compatible with multiple Minecraft versions, see the `versions/aliases.txt` for
an exact mapping, or simply compare with the next available Minecraft versions above and below your version.

Note that not all files in your `.minecraft` folder are updated at the same time, so some of them may be from older
Essential versions.

Note that if your installation of Essential is older, there may still be mod and loader files in there from before
source code has been made publicly accessible and even from before builds were deterministic.
If you still have such files and are concerned about them, please get in contact with us and we will try to verify its
authenticity.

### Files in the .minecraft/mods folder

If your jar file is smaller than one megabyte (typically includes a Minecraft version but never an Essential version in
its name),
it should be an [Essential Container](#building-essential-container) file.
Please refer to the linked section for which "platform" corresponds to your Minecraft + Mod Loader and where the built
jar file can be found.

If your jar file is much larger (typically includes both the Minecraft version and the Essential version in its name),
it should be the `pinned_` file found in `versions/<MC-Version>/build/libs/`.

### Files in the .minecraft/essential folder

If your file is named `Essential (<Mod-Loader>_<MC-Version>).jar`,
it should be the main `Essential ` (not the `pinned_`) file found in `versions/<MC-Version>/build/libs/`.

If your file is named `Essential (<Mod-Loader>_<MC-Version>).processed.jar`,
it is a temporary file derived from the above file with the same name without the `processed` suffix.
If you delete it, it will be re-generated from that file on next launch.

### Files in the .minecraft/essential/libraries folder

These are extracted from the main Essential jar [in the .minecraft/essentials](#files-in-the-minecraftessential-folder)
(from its `META-INF/jars/` folder, as well as recursively for those jars).
If you delete them, those that are still used by your current Essential version will be re-extracted on next launch.

### Files in the .minecraft/essential/loader folder

If your file is named `stage1.jar`, it is extracted from
[the mods in your .minecraft/mods](#files-in-the-minecraftmods-folder) folder and from the main Essential jar
[in the .minecraft/essentials](#files-in-the-minecraftessential-folder) folder (whichever has the most recent version).
In either case, unless you are on an ancient Essential version, it should match the jar file found in
`loader/stage1/<platform>/build/libs/` where `<platform>` is one of the four listed in
[this section of the README](#building-essential-container).

If your file is named `stage2.<Mod-Loader>_<MC-Version>.jar`, it should match the jar file found in
`loader/stage2/<platform>/build/libs/` where `<platform>` is one of the four listed in
[this section of the README](#building-essential-container).
Note that this file in particular is not at all updated in lockstep with Essential, so its version may very well be
older or even newer than the one which this repo references for your Essential version.
It should usually be accompanied by a `.meta` file though which should contain its version (not the Essential version),
you may then be able to find this version in the `loader` repository and build it specifically.
Note that even though you found this file is in the `stage1` folder, it is the `stage2` of the loader (the reason it is
in the `stage1` folder is because `stage1` loads it).

## License

Below, you'll find an outline of what is permitted and what is restricted under the source-available license of the
Essential Mod's source code.

**What you CAN do**

- Audit the source code.
- Compile the source code to confirm the authenticity of the official releases.
- Create unmodified forks of the repository.

**What you CANNOT do**

- Utilize any code or assets, including for personal use.
- Incorporate the source code in any other projects or use our code as a reference in new projects.
- Modify or alter the source code provided here.
- Distribute compiled versions of the source code or modified source code.

This summary is not an exhaustive interpretation of the license; for a comprehensive understanding, please refer to [the
full license file](https://github.com/EssentialGG/Essential/blob/main/LICENSE).
