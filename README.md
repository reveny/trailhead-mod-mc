<div align="center">

<img src="art/banner.jpg" alt="Trailhead" width="100%">

<br>

[![Build](https://github.com/reveny/trailhead-mod-mc/actions/workflows/build.yml/badge.svg)](https://github.com/reveny/trailhead-mod-mc/actions/workflows/build.yml)
![Minecraft 1.21.8](https://img.shields.io/badge/Minecraft-1.21.8-62b47a?style=flat)
![Fabric](https://img.shields.io/badge/Loader-Fabric-dbd0b4?style=flat)
[![License: MIT](https://img.shields.io/badge/License-MIT-f2b252?style=flat)](LICENSE)

**Trailhead replaces Minecraft's overworld with landscapes inspired by real national parks.**<br>
Dolomite meadows under limestone towers, Teton fault ranges, Pacific Northwest rainforest,<br>
Norwegian fjords and Atlantic sea cliffs, connected by rivers that actually flow downhill to the sea.

</div>

---

## Features

### 16 Different landscape regions

The world is split up into regions that are a few kilometres in size. Each of the regions is inspired by a real place. Regions can only appear next to each other if the climate allows it. That means that a desert will never border an ice biome.

The following regions exist:

- Dolomites
- Alpine valley
- Tetons
- Cascadia
- Canyonlands
- Fjordlands
- Sea cliffs
- Temperate hills
- Prairie
- Steppe
- Savanna
- Desert
- Jungle
- Boreal forest
- Tundra
- Ice sheet

### Climate that makes sense

The mod includes 2 world types:

- Latitude: It gets colder the more north you go
- Climate Zones: Climate regions exist in every direction

Climate zones are about 20+ km across.

### Rivers, lakes and waterfalls

- Every river flows downhill into the sea.
- Lakes form in natural basins and are rare in deserts.
- Glarial valleys and drowned fjords exists depending on the region.

### Mountains and coastlines

- Long mountain ranges that rise out of foothills
- Dolomite towers, mesas and buttes with scree slopes at their base
- Continental shelves, deep ocean basins, volcanic island chains, coral atolls and rocky archipelagos
- Sheer sea cliffs with wave-cut shelves and sea stacks

### Biomes and vegetation

- 31 custom biomes with region specific trees, ground cover, rock colours and water tints
- Golden larches, aspens, junipers, palms, cacti, ice spikes, boulders and wildflower meadows
- Cliff faces show the region's own rock layers: basalt, granite, limestone, sandstone

## Installation

For now it's fabric and 1.21.8 only. I'll expand it as soon as possible.

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.18.4 or newer for Minecraft 1.21.8.
2. Download [Fabric API](https://modrinth.com/mod/fabric-api) and Trailhead from the [releases page](https://github.com/reveny/trailhead-mod-mc/releases), and put both jars in your `mods` folder.
3. Create a new world and pick either "Trailhead: Latitude" or "Trailhead: Climate Zones" under World Type.

For server use, install the mod on the server before creating the world. Server compatibility is untested so far.

## Compatibility

- This mod only works with new worlds. It replaces the world generator so it can't be added to an existing world without creating issues.
- This mod is not compatible with other overworld generation mods such as Terralith or Tectonic.

## Building from source

Requires JDK 21.

```sh
./gradlew build
```

The jar ends up in `build/libs/`.

### Terrain preview tool

This is a small helper tool that renders real chunks without having to start the game.

```sh
./gradlew terrainPreview --args="--seed=12345 --width=2048 --depth=2048 --out=build/terrain-debug/preview"
./gradlew terrainPreview --args="--seed=12345 --check=40 --map=false"
./gradlew terrainPreview --args="--seed=12345 --worldmap=150 --climate=zones"
./gradlew terrainPreview --args="--seed=12345 --find=60000 --map=false"
```

`--check` fails the run if it finds chunk seams or one-block spikes.

## License

Trailhead is released under the [MIT License](LICENSE).

## Gallery

<table>
  <tr>
    <td colspan="2"><img src="art/gallery/alpine-valley.jpg" alt="Alpine valley"><br><sub><b>Alpine valley</sub></td>
  </tr>
  <tr>
    <td width="50%"><img src="art/gallery/dolomites.jpg" alt="Dolomites"><br><sub><b>Dolomites</sub></td>
    <td width="50%"><img src="art/gallery/savanna.jpg" alt="Savanna"><br><sub><b>Savanna</sub></td>
  </tr>
  <tr>
    <td><img src="art/gallery/canyonlands.jpg" alt="Canyonlands"><br><sub><b>Canyonlands</sub></td>
    <td><img src="art/gallery/sea-cliffs.jpg" alt="Sea cliffs"><br><sub><b>Sea cliffs</b> · basalt walls and sea stacks on an Atlantic coast</sub></td>
  </tr>
  <tr>
    <td><img src="art/gallery/jungle.jpg" alt="Jungle"><br><sub><b>Jungle</sub></td>
    <td><img src="art/gallery/desert.jpg" alt="Desert"><br><sub><b>Desert</sub></td>
  </tr>
  <tr>
    <td><img src="art/gallery/steppe.jpg" alt="Steppe"><br><sub><b>Steppe</sub></td>
    <td><img src="art/gallery/atoll.jpg" alt="Atoll"><br><sub><b>Atolls</sub></td>
  </tr>
  <tr>
    <td><img src="art/gallery/dripstone-caves.jpg" alt="Dripstone caves"><br><sub><b>Dripstone caves</sub></td>
    <td><img src="art/gallery/ice-caves.jpg" alt="Ice caves"><br><sub><b>Ice caves</sub></td>
  </tr>
</table>

<sub>Screenshots taken with <a href="https://modrinth.com/shader/complementary-reimagined">Complementary Reimagined</a> and <a href="https://modrinth.com/mod/distanthorizons">Distant Horizons</a>.</sub>