<p align="center"><img src="src/main/resources/assets/icons/icon_256.png" /></p>

# ArdaBiomesEditor
A biome color mapping editor for Ardacraft and Polytone

## Installation

Download the latest release and unzip the archive. Run `ArdaBiomesEditor.exe` to start the application.

## Usage

- `File` → `Open...` to load the biome mappings from a [resource pack](https://github.com/ArdaCraft/ACRP).
- The list of biomes are displayed on the left column. The color key for the biome is displayed on the center pane.
- A biome color key must be saved in the resource pack before editing a new biome (`File` → `Save` or `Save` on the bottom left column)

> [!IMPORTANT]
> Editing a resource pack while the game is running **is possible** but a new resource pack version will be created in the same folder if the resource pack is in use. The app will automatically switch to the new version.

## Packaging and release

This project uses jlink, jpackage and wix to create a Windows runtime image.

- Run `mvn clean package javafx:jlink` to generate a shaded jar and create a custom runtime image in the target directory.
- The shaded jar should be located at `target\dist\`
- The custom runtime image should be located at `target\image\`

A release build can be created with the following command from the project root:

```bash
jpackage --type app-image \
  --input target\dist \
  --main-jar ArdaBiomeEditor-<version>.jar \
  --runtime-image target\image \
  --icon src\main\resources\icon.ico \
  --name ArdaBiomesEditor \
  --dest target\release \ 
  --main-class com.duom.ardabiomeseditor.ArdaBiomesEditor
```

## License

This work is licensed under the CC-BY 4 License

[![License: CC BY 4.0](https://img.shields.io/badge/License-CC_BY_4.0-lightgrey.svg)](https://creativecommons.org/licenses/by/4.0/)

### Third-Party Assets

This project includes icons licensed under the Apache License 2.0.

- [Material Design Icons](https://icon-sets.iconify.design/mdi/) by Pictogrammers
  - License: Apache License, Version 2.0
  - License text: [LICENSE](assets/icons/mdi/LICENSE)
- [Remix Icon icon set](https://icon-sets.iconify.design/ri/) by Remix Design
  - License: Apache License, Version 2.0
  - License text: [LICENSE](assets/icons/ri/LICENSE)
- [Tabler Icons](https://icon-sets.iconify.design/tabler/) by Paweł Kuna
  - License: MIT License
  - License text: [LICENSE](assets/icons/tabler/LICENSE)