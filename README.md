<h1 align="center">Tanaw</h1>

<p align="center">
  <strong>A Philippine based earthquake and typhoon alert system</strong>
</p>

<br>

Tanaw is a disaster alert system for typhoons and earthquakes, built using Java 21 and Spring Boot 4. This project aims to provide alerts for its users whenever an earthquake occurs near their specified location or if there's an upcoming typhoon that'll affect their area through modern means.

> [!NOTE]
> **Note: Tanaw is currently still under development. This repository is made to show my journey while developing it.**

---

## Why does it exist?

An average of 20 typhoons per year enter the Philippine Area of Responsibility (PAR); roughly 8 to 9 of those typhoons hit or cross our land, leading to floods, landslides, and deaths of our fellow Filipinos. The Philippines also sits on top of the Pacific Ring of Fire, making our country prone to tectonic shifts causing earthquakes.

The aim of this project is to create an alert system for Filipinos that will notify them about disasters that occurred or will occur within their area and notify them through modern channels like email, Telegram, and Discord.

---

## Tech Stack

The planned tech stack for this project is Java 21 through the Spring Ecosystem (Spring Boot, Spring Data JPA, Spring Security), PostgreSQL for the database, Flyway as a database migration tool, and docker for creating containers. This will expand further more as new features and functions gets implemented.

<br>

<p align="center">
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/java/java-original.svg" width="70" alt="Java">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/spring/spring-original.svg" width="70" alt="Spring">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/postgresql/postgresql-original.svg" width="70" alt="PostgreSQL">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="https://cdn.jsdelivr.net/gh/devicons/devicon/icons/docker/docker-original.svg" width="70" alt="Docker">
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src=https://upload.wikimedia.org/wikipedia/commons/e/e1/Flyway_logo.svg?utm_source=commons.wikimedia.org&utm_campaign=index&utm_content=original width="70" alt="Flyway">
</p>

<p align="center">
  <sub>
    <b>Java</b>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <b>Spring Ecosystem</b>
    &nbsp;&nbsp;&nbsp;&nbsp;
    <b>PostgreSQL</b>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <b>Docker</b>
    &nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;
    <b>Flyway</b>
  </sub>
</p>

---

## Planned Features

| Feature                        |
| ------------------------------ |
| Earthquake monitoring          |
| Typhoon monitoring             |
| PSGC-based geographic matching |
| PostGIS radius queries         |
| Email notifications            |
| Discord notifications          |
| Telegram notifications         |

