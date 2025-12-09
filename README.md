<div style="text-align: center;">
  <img src="src/main/resources/icon_256.png" />
</div>

# ArdaBiomesEditor
A biome color mapping editor for Ardacraft and Polytone



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