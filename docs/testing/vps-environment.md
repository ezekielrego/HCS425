# VPS environment record

Captured during development on 2026-10-19:

* Ubuntu 24.04.4 LTS, Linux 6.8.0-147-generic, x86_64
* 4 logical CPU cores, 7.8 GiB RAM, approximately 71 GiB free at inspection
* OpenJDK 21.0.12.1, `javac` 21.0.12
* Apache Maven 3.8.7
* Git 2.43.0, Graphviz 2.42.2, Xvfb already present, X11VNC 0.9.16 installed but never exposed or configured
* NetBeans was not installed; Maven/JavaFX/NetBeans action compatibility was verified instead

Development packages installed with apt were OpenJDK 21, Maven, Graphviz, X11VNC, graphics/font runtime dependencies, and a Git update. They were retained for the final build/runtime evidence. If the VPS should be returned to its pre-development state, review package dependencies first, then remove only the explicitly added tools with `apt remove openjdk-21-jdk maven graphviz x11vnc` and run `apt autoremove` only after checking the proposed list. Do not remove Java/Maven if the project is still expected to run on this VPS.
