# Changelog

## [1.1.0](https://github.com/cyljacky02/loafy-lib/compare/v1.0.0...v1.1.0) (2026-08-10)


### Features

* **animation:** add cinematic animation system ([b03b5c4](https://github.com/cyljacky02/loafy-lib/commit/b03b5c428d45b686b71ab80bd98d1de4d4b42599))
* **animation:** harden camera control against interaction and disconnect ([e1e3935](https://github.com/cyljacky02/loafy-lib/commit/e1e39352f96db2285a1bdbcbbf8539075ef503be))
* **combat:** add reusable combat tagging service ([a43ef73](https://github.com/cyljacky02/loafy-lib/commit/a43ef73d50d132c8924d53b170aea1f2be03aeed))
* **config:** add Duration serializer and generic-safe data class discoverer ([520d7bd](https://github.com/cyljacky02/loafy-lib/commit/520d7bd87335aefabaa7f75544507c70fbf14ace))
* **config:** upgrade Configurate to 4.3.0-SNAPSHOT with Kotlin support ([fd0e2da](https://github.com/cyljacky02/loafy-lib/commit/fd0e2da82d95e824196b14bce4dbd270ae72d597))
* **database:** add SQLite support and extract shared Hikari base class ([85a7ee0](https://github.com/cyljacky02/loafy-lib/commit/85a7ee007db44b90aa81fc2688ec905b090a6bae))
* improve database config and add kotlinx-serialization ([75ea31f](https://github.com/cyljacky02/loafy-lib/commit/75ea31fb1ce8e3a4f19b1eeba904889d04759ddd))
* **location:** add Folia-compatible safe location utilities ([8d64059](https://github.com/cyljacky02/loafy-lib/commit/8d640595a3cb1d05d9be2354a269687613c5732d))
* **location:** add HeightMap-based surface detection for RTP ([89af8e9](https://github.com/cyljacky02/loafy-lib/commit/89af8e943d91c2281c8f4ca0f76bdefc0a370821))
* **pdc:** add TileState extensions and document thread requirements ([1286969](https://github.com/cyljacky02/loafy-lib/commit/128696967806e7c6a637427c3b730c6e8da2ecef))
* **permission:** add thread-safe permission provider detection ([362261f](https://github.com/cyljacky02/loafy-lib/commit/362261fc0ec4557d702195fc68011d65e4783c2e))
* **protection:** add unified build permission hook ([351fb49](https://github.com/cyljacky02/loafy-lib/commit/351fb49e040435f081572a038231f9a375216c0b))
* **redis:** add getdel to RedisPipeline ([c9bf266](https://github.com/cyljacky02/loafy-lib/commit/c9bf266e6d3295c9ef62c16dbff29d707f2de205))
* **redis:** add retry helper with exponential backoff ([1afe13e](https://github.com/cyljacky02/loafy-lib/commit/1afe13ee72cbdf5ec6f5d814000b88a03efc06d0))
* **redis:** add ScriptOutputType param and expand RedisPipeline ops ([217be9c](https://github.com/cyljacky02/loafy-lib/commit/217be9ce69efc6c05503345d527473a1a525f7bd))
* **scheduler:** add Entity.delayTicks() for Folia-compatible delays ([5059021](https://github.com/cyljacky02/loafy-lib/commit/50590219c641de6271c3f41b9ead97c114452426))
* **sound:** add config-driven sound feedback helper ([97a2c9b](https://github.com/cyljacky02/loafy-lib/commit/97a2c9b5c5ce0ea3056ec9acc4069bb2f8bec817))


### Bug Fixes

* **blockdata:** scope dirty block tracking per plugin ([a8650c6](https://github.com/cyljacky02/loafy-lib/commit/a8650c607a0a53f946b9579e224c67d7efeb6c99))
* **event:** resume suspend handlers on the owning region thread ([7e76523](https://github.com/cyljacky02/loafy-lib/commit/7e765237f06e665a117ae6294584d6fde6d378ae))
* **glow:** use Paper's nextEntityId() for virtual entities ([79658fa](https://github.com/cyljacky02/loafy-lib/commit/79658fa914749526bd9f82014b9607af2796b7ad))
* **loader:** declare optional soft dependencies and force load order before Nexo ([723bb68](https://github.com/cyljacky02/loafy-lib/commit/723bb68caacd6de5538a92af15b3c29d1461b7e8))
* **location:** reject hazardous fluids via FluidData ([a95010e](https://github.com/cyljacky02/loafy-lib/commit/a95010e4231def0cb9520097b4ef9b962ed49553))
* **location:** simplify body space safety check ([c4522d8](https://github.com/cyljacky02/loafy-lib/commit/c4522d819083904e0bba34764a7d28d910445e18))
* **plugin:** cancel the plugin scope before component shutdown ([eda64db](https://github.com/cyljacky02/loafy-lib/commit/eda64db333b1f2ac32d22bded58bc8104bb0d2cb))
* resolve Lettuce netty-resolver-dns classloading issue ([397bb47](https://github.com/cyljacky02/loafy-lib/commit/397bb4705f73fc5eb6f6efe3eb7b8d695ab49868))


### Refactoring

* centralize PDC extensions into dedicated pdc/ package ([59f6748](https://github.com/cyljacky02/loafy-lib/commit/59f6748e08e972128a6d24f16a7d285f8d88d271))
* **glow:** key the API by entity ID and preserve non-glow entity flags ([cf6c4d7](https://github.com/cyljacky02/loafy-lib/commit/cf6c4d71a165e6ff15c8938abbdce4e5d8250716))
* **scheduler:** cache dispatchers and hold plugins weakly ([83cfef1](https://github.com/cyljacky02/loafy-lib/commit/83cfef152df74d4a00b197c7b0ef67d6e7c9652b))
* standardize component lifecycle and fix MiniMessage tags ([34a8795](https://github.com/cyljacky02/loafy-lib/commit/34a8795efc35dc30d9189ad69bc66e7600d717e6))
* **util:** build player heads via the PROFILE data component ([bddee88](https://github.com/cyljacky02/loafy-lib/commit/bddee888a35870e655294b5247c593f509caff9a))
* **util:** extract generic KeyedCooldowns and build PlayerCooldowns on it ([958be09](https://github.com/cyljacky02/loafy-lib/commit/958be093ffa26fbbfcfc0c122fd0691fd08fd278))


### Documentation

* add getdel to RedisPipeline operations ([4470240](https://github.com/cyljacky02/loafy-lib/commit/4470240190964cdb9f378df5cd43f2a7f4bc1430))
* add thread safety documentation to services ([97bf41b](https://github.com/cyljacky02/loafy-lib/commit/97bf41b882f313ee2a0034490ab81aba87df96bf))
* cover protection hook, SQLite and the database factory ([3e83218](https://github.com/cyljacky02/loafy-lib/commit/3e8321800f3b6f111b33912556598d9794c4f6b7))
* document Residence, GriefPrevention and LuckPerms integrations ([19681b4](https://github.com/cyljacky02/loafy-lib/commit/19681b439417edafc0b9eaf47516ce775726623d))
