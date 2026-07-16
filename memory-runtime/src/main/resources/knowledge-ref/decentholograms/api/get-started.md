---
title: Get Started
tags: DecentHolograms, api
source: https://wiki.decentholograms.eu/api/get-started/
---
# Get Started

## How to get started with DecentHolograms' API

On this page, you can find how to add DecentHolograms into your plugins and use its API. Keep in mind, that you also need to have the DecentHolograms plugin on your server, in order for the API to work.

Latest version of the plugin can be found in Releases on the GitHub page.

## Add the API

Add the following to your build file to add the DecentHolograms API to your project.

Gradle Maven

build.gradle

```
repositories {
    maven { 
      id = "jitpack"
      url = "https://jitpack.io/"
    }
}

depencencies {
    compileOnly 'com.github.decentsoftware-eu:decentholograms:{version}'
}
```

pom.xml

```
<repositories>
  <repository>
    <id>jitpack</id>
    <url>https://jitpack.io/</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.github.decentsoftware-eu</groupId>
    <artifactId>decentholograms</artifactId>
    <version>{version}</version>
    <scope>provided</scope>
  </dependency>
</dependencies>
```

Receiving errors about NBT-API not being found?

Try adding the CodeMC repository to your build file to fix this:

Gradle Maven

build.gradle

```
repositories {
  // Other repositories, including jitpack
  maven {
    id = "codemc"
    url = "https://repo.codemc.io/repository/maven-public/"
  }
}
```

```
<repositories>
  <!-- Other repositories, including jitpack -->
  <repository>
    <id>codemc</id>
    <url>https://repo.codemc.io/repository/maven-public/</url>
  </repository>
</repositories>
```

## Add plugin as a (soft) dependency

DecentHolograms needs to be on your server for your plugin to be able to use the API.  
To make sure that DecentHolograms is loaded before your plugin, add it as a (soft) dependency to your `plugin.yml` or `paper-plugin.yml` file:

/ plugin.yml paper-plugin.yml

Soft dependencyDependency

```
name: 'MyPlugin'
author: 'Me'
version: '1.0.0'

main: 'com.example.plugin.MyPlugin'

softdepend:
  - DecentHolograms
```

```
name: 'MyPlugin'
author: 'Me'
version: '1.0.0'

main: 'com.example.plugin.MyPlugin'

depend:
  - DecentHolograms
```

Soft dependencyDependency

```
name: 'MyPlugin'
author: 'Me'
version: '1.0.0'

main: 'com.example.plugin.MyPlugin'

dependencies:
  server:
    DecentHolograms:
      load: BEFORE
      required: false # This is the default when not present
```

```
name: 'MyPlugin'
author: 'Me'
version: '1.0.0'

main: 'com.example.plugin.MyPlugin'

dependencies:
  server:
    DecentHolograms:
      load: BEFORE
      required: true
```
