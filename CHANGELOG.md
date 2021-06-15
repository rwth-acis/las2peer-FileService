# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/), and this project adheres
to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Releases up to 2.2.5 are only documented on
the [GitHub Release Page](https://github.com/rwth-acis/las2peer-FileService/releases)

## [Unreleased]

### Breaking Changes

- POST now longer accepts an identifier but instead generates a UUID for each upload. If the identifier should to be set
  by the client, it is recommended to use PUT instead [#8](https://github.com/rwth-acis/las2peer-FileService/pull/8).
- Endpoint changes ([#8](https://github.com/rwth-acis/las2peer-FileService/pull/8)):
    - The whole services now registers as `files` and no longer as `fileservice`
    - The previous `files` endpoints have been moved to top level.
    - The implication of this change is that uploads directed at `/fileservice/files` now have to be sent to `/files`
      instead.

### Changed

- Updated las2peer to 1.0.0 [#5](https://github.com/rwth-acis/las2peer-FileService/pull/5)
- Update las2peer to 1.1.0 [#6](https://github.com/rwth-acis/las2peer-FileService/pull/6)
- Changed required Java Version to 14 [#6](https://github.com/rwth-acis/las2peer-FileService/pull/6)
- Build Process is now based on gradle [#6](https://github.com/rwth-acis/las2peer-FileService/pull/6)

## [2.2.5] - 2018-02-27

See GH Releases
