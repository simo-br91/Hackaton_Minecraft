# Hackaton Minecraft – AI Mentor Mod 👨‍🏫🧠

This repository contains our hackathon Minecraft mod built on top of the **Minecraft Forge MDK** (1.20.1).  
The goal of the project is to add an **AI-powered mentor inside Minecraft** (a “virtual teacher” / helper NPC) that players can talk to directly in game.

The mod takes care of the Minecraft/Forge side (entities, events, UI), and connects to an **external backend** (e.g. a local Python server calling an LLM API) to generate answers in natural language.

> ⚠️ This is an hackathon prototype: the code is focused on fast iteration and a working demo rather than production-grade architecture.

---

## Features

- 🧑‍🏫 **AI Mentor NPC / Interaction**
  - Custom Minecraft interaction (e.g. right-click on NPC / open chat screen).
  - Player sends questions from inside the game.
  - Mod sends the question to an external HTTP backend and displays the answer.

- 🌐 **External AI Backend Integration**
  - Simple HTTP calls from the mod to a local server (e.g. `localhost`).
  - Backend can call any Large Language Model API (OpenAI / Gemini / etc.).
  - Response is returned as plain text and rendered in the Minecraft UI.

- ⚙️ **Forge / Gradle Project**
  - Based on the official Forge MDK for Minecraft **1.20.1**.
  - Gradle project ready for IntelliJ or Eclipse.
  - Standard Forge tasks (`runClient`, `runServer`, `build`) available.

---

## Tech Stack

- **Minecraft**: Java Edition 1.20.1  
- **Mod Loader**: Minecraft Forge (MDK)  
- **Language**: Java  
- **Build**: Gradle  
- **Backend (optional, external)**: e.g. Python (FastAPI / Flask) or Node.js for calling an LLM API

---

## Getting Started

### 1. Prerequisites

- Java 17 (required for Minecraft 1.20.x / Forge)
- Git
- An IDE:
  - IntelliJ IDEA (recommended) or
  - Eclipse
- Gradle **wrapper is already included** (`gradlew`, `gradlew.bat`) – no global Gradle install required.

### 2. Clone the repository

```bash
git clone https://github.com/simo-br91/Hackaton_Minecraft.git
cd Hackaton_Minecraft
