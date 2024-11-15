# FullJarDecompiler

## Overview

FullJarDecompiler is a plugin for IntelliJ IDEA that decompiles all Java classes of a jar file to Java source zip.

## Features

- Decompiles all Java classes in a jar file and saves them to a Java source zip
- Uses built-in Java decompiler

## Installation

1. Clone the repository:
    ```sh
    git clone https://github.com/coperius/fullJarDecompiler.git
    ```
2. Open the project in IntelliJ IDEA.
3. Build the project using Gradle:
    ```sh
    ./gradlew build
    ```

## Usage

1. Open a Java project in IntelliJ IDEA.
2. Install the plugin by going to `File` -> `Settings` -> `Plugins` -> `Install Plugin from Disk...` and selecting the `build/libs/fullJarDecompiler.jar` file.
3. Select a jar file in the project view and right-click on it.
4. Click on the "Decompile Jar File" option in the context menu.
5. You must accept the license agreement to use the built-in Java decompiler for the first time.
6. Select a directory to save the Java source zip.
7. Click on the "Open" button.
8. The Java source zip will be saved in the selected directory.

## License

This project is licensed under the MIT License. See the `LICENSE` file for details.