<p align="center"><img src="src/main/resources/assets/icons/icon_256.png" /></p>

# ArdaBiomesEditor
A biome color mapping editor for Ardacraft and Polytone

## Installation

Download the latest release and unzip the archive. Run `ArdaBiomesEditor.exe` to start the application.

## Usage

- `File` → `Open Folder...` to load a [resource pack](https://github.com/ArdaCraft/ACRP). The resource pack needs to contain [polytone](https://github.com/MehVahdJukaar/polytone) definitions, see the [Polytone Sample Pack](https://github.com/MehVahdJukaar/polytone/tree/1.21.1/polytone_sample_pack) for a working example.

<p align="center"><img src="https://github.com/user-attachments/assets/96a64b56-94bf-4702-ad8f-0244a636f7a8" /></p>

### Resource Tree

The left pane displays the resource tree of the loaded resource pack. The tree can be filtered using the dropdown.

The displayed resources can be one of the following types:
- <img src="src/main/resources/assets/icons/mdi/mdi--folder.svg" alt="Folder icon" width="16" height="16"/> Directory : a folder
- <img src="src/main/resources/assets/icons/mdi/mdi--cog.svg" alt="Modifier icon" width="16" height="16"/> Modifier : a modifier json definition
- <img src="src/main/resources/assets/icons/mdi/mdi--color.svg" alt="Colormap icon" width="16" height="16"/> Colormap : a colormap json definition (inlined or standalone)
- <img src="src/main/resources/assets/icons/mdi/mdi--map-legend.svg" alt="Biome mapper icon" width="16" height="16"/> Biome mapper : a biome id mapper (inlined or standalone)

### Colormap Editor

The colormap editor allows editing of colormaps and biome mappings.

#### Keybinds

> **Canvas Editor**
>
> | Shortcut            | Action              |
> |---------------------|---------------------|
> | `CTRL` + `Click`    | Select multiple     |
> | `CTRL` + `A`        | Select all          |
> | `CTRL` + `D`        | Deselect            |
> | `CTRL` + `I`        | Invert selection    |
> | `CTRL` + `H`        | Hide column         |
> | `ALT` + `H`         | Unhide all          |
>
> **Mouse navigation**
>
> | Shortcut                 | Action        |
> |--------------------------|---------------|
> | `CTRL` + `Scroll`        | Zoom in / out |
> | `Middle mouse click`     | Panning       |
>
> **Bottom toolbar**
>
> - <img src="src/main/resources/assets/icons/tabler/tabler--arrow-autofit-content-filled.svg" alt="Biome mapper icon" width="16" height="16"/> Fit all the columns into view
> - <img src="src/main/resources/assets/icons/tabler/tabler--arrow-autofit-height-filled.svg" alt="Biome mapper icon" width="16" height="16"/> Fit all the rows into view
> - <img src="src/main/resources/assets/icons/mdi/mdi--checkerboard.svg" alt="Checkerboard icon icon" width="16" height="16"/> Display checkerboard (for transparency editing)
>
> **Sliders**
>
> - Double click on the thumb resets the slider to 0


#### Center panel

- When a **colormap** resource is selected, the editor displays the associated texture and its mappings (if defined in json).
- When a **biome** mapper resource is selected, the editor display each corresponding mapped biome from each colormap referencing this mapper.

#### Right panel

- The right panel displays the list of mappings (columns) of the colormap.
- When one or multiple mappings are selected, a Hue/Saturation/Brightness color picker is displayed to edit the selected mapping(s).

#### Bottom toolbar

- Allow zooming in / out of the colormap texture.
- Shows the current selection 
- <img src="src/main/resources/assets/icons/mdi/mdi--checkerboard.svg" alt="Biome mapper icon" width="16" height="16"/> Displays a checker pattern (for transparency) behind the colormap texture.

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
  --icon src\main\resources\assets\icons\icon.ico \
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
