# Doxygen Python Docstrings (PyCharm plugin)

Local IntelliJ Platform plugin adding support for Doxygen-style tags in Python docstrings
(`@brief`, `@param`, `@return`, `@throws`, `@note`, ...). PyCharm's built-in docstring formats
(Plain / reST / NumPy / Google) don't understand these tags. Not published on the Marketplace.

No maintained Marketplace plugin covered this when the project started (searched "Doxygen Python docstring").

## Features

1. **Intention**: Alt+Enter on a function without docstring → *Add Doxygen docstring*.
   One `@param` per parameter (`self`/`cls` skipped, `*args`/`**kwargs` without stars),
   `@return` omitted when annotated `-> None`, caret after `@brief`.
2. **Inspection** *Doxygen @param tags do not match signature* (Settings → Editor → Inspections → Python):
   missing, unknown (typo) and duplicate `@param` tags. Quick-fixes: add missing tags in signature
   order, rename or remove an unknown tag, remove a duplicate. Docstrings with no `@param` are not checked.
3. **Highlighting** of tags and of the argument after `@param`/`@throws`/... in docstrings.
   Colors: Settings → Editor → Color Scheme → *Doxygen (Python)*.
4. **Quick Documentation** (Ctrl+Q): signature with annotations, brief, parameter table, returns,
   raises, other tags. Docstrings without Doxygen tags fall back to PyCharm's rendering.
   This part depends on PyCharm internals; turn it off on its own with the registry key
   `doxygen.docstrings.quickDoc` (Help → Find Action → *Registry...*).

## Supported versions

PyCharm (Community / Professional / unified) **2026.2.x** — builds `262` to `262.*`
(`pluginSinceBuild` / `pluginUntilBuild` in `gradle.properties`). Developed and tested on 2026.2.4 (262.11879.98).
To target a new release, bump `platformVersion` and the build range, then run the tests.

## Build

Requires JDK 21.

```bash
./gradlew buildPlugin            # downloads PyCharm 2026.2.4 to compile against
./gradlew buildPlugin -PplatformLocalPath=/path/to/pycharm   # or reuse an installed one
```

Output: `build/distributions/pycharm-doxygen-docstrings-<version>.zip`.

## Install

Quick install (builds, then unzips into the PyCharm 2026.2 plugin directory; restart the IDE afterwards):

```bash
./install.sh                    # Linux/macOS; also finds Gateway (remote-dev) PyCharm backends
./install.sh --no-build ~/.local/share/JetBrains/PyCharm2026.2   # explicit target
```

```powershell
.\install.ps1                   # Windows, %APPDATA%\JetBrains\PyCharm2026.2*\plugins
```

Manual: Settings → Plugins → ⚙ → *Install Plugin from Disk…* → pick the `.zip`.
With JetBrains Gateway, install it on the **host** (backend) side, then restart the backend.

## Tests

```bash
./gradlew test [-PplatformLocalPath=/path/to/pycharm]
```

`BasePlatformTestCase` tests run the real PyCharm Python PSI and extension points: the stub
generation, the inspection warnings and quick-fixes, highlighting, the Quick Documentation HTML,
and that the platform resolves our documentation provider ahead of the Python plugin's (the part
most likely to break on PyCharm updates). Tests run headless (`java.awt.headless=true`).
