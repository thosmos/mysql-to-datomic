# [mysql-to-datomic](https://github.com/thosmos/mysql-to-datomic)

## [Unreleased]
### Planned
- more robust FK tracking to enable better interlinked multi-table updates 

## [0.3.5] - 2020-01-03
### Fixed
- standalone app usage ...
### Changed
- improved README and comment code in core.clj
- removed project.clj; uses only deps.edn

## [0.3.4] - 2019-02-02
### Added
- some fns in core for moving new data from mysql into existing matching datomic schema
- use specs for doing some of the migration heavy lifting
- CHANGELOG (so meta ...)

## [0.3.3] - 2018-12-02
### Changed 
- updated deps

## [0.3.1] - 2018-12-02
- can update an existing migration with new data
- generates and uses a compound-key on many-to-many lookup tables

## 0.3.0  - 2018-03-11
- initial commit 
- worked for getting a bunch of simple schemas and data into Datomic

[Unreleased]: https://github.com/thosmos/mysql-to-datomic/compare/0.3.5..HEAD
[0.3.5]: https://github.com/thosmos/mysql-to-datomic/compare/0.3.4...0.3.5
[0.3.4]: https://github.com/thosmos/mysql-to-datomic/compare/0.3.3...0.3.4
[0.3.3]: https://github.com/thosmos/mysql-to-datomic/compare/0.3.1...0.3.3
[0.3.1]: https://github.com/thosmos/mysql-to-datomic/compare/0.3.0...0.3.1

#### Change Log
All notable changes to this project will be documented in this file. This change log follows the conventions of [keepachangelog.com](http://keepachangelog.com/).

### Planned
### Changed
### Removed
### Fixed
### Added
