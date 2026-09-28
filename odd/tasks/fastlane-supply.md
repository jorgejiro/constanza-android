# Feature: publish to Google Play with fastlane supply

## Objective

Upload Constanza's whole Play listing (texts, 42 screenshots, icon, feature graphics, release notes)
and later AABs from this repo with `fastlane supply`, instead of by hand in Play Console.

## Why

The listing material already lives in `fastlane/metadata/android/` (PR #131). Uploading 42 images by
hand is slow and error-prone; the Google Play Developer API does it in one command.

## Scope

- In: `fastlane/Appfile`, `fastlane/Fastfile` (lanes: validate, metadata-only upload, AAB to a
  track), tablet screenshots copied into `sevenInchScreenshots/` and `tenInchScreenshots/`, key
  file kept out of git, Spanish doc on how to run it.
- Out (owner-only, the API cannot do them): creating the app in Play Console, the first manual AAB
  upload, the service account + JSON key, the "App content" questionnaires. Data safety also stays
  manual: the API endpoint needs the CSV template exported from Play Console, and for this app the
  form is a single "No".

## Constraints

- fastlane installed with Homebrew (system Ruby 2.6 is too old).
- The service-account key never enters the repository; path outside the repo.
- No upload is run until the owner has created the app, uploaded the first AAB and provided the key.
- Owner confirmed (2026-09-28) the developer account predates 2023-11-13: no 12-tester closed test.

## TDD

Off — tooling/config only. Checks: `fastlane lanes` lists the lanes; a dry validation lane runs
without credentials where possible; screenshot copies match the source set byte for byte.

## Tasks

- [x] T1 — Install fastlane, add Appfile/Fastfile, tablet screenshot copies, .gitignore for the key,
      doc. Route: delegated (writer trigger).
- [ ] T2 — First real upload once the owner provides the key (metadata + images, then AAB to
      internal/production as the owner decides). Route: inline, bounded action.

## Progress / evidence

- T1 done, commit `9681c8e`: fastlane 2.240.1 (Homebrew); `fastlane/Appfile` (key from
  `SUPPLY_JSON_KEY`, default `~/.config/play/jjrmobileapps.json`); `fastlane/Fastfile` lanes
  `validar`, `ficha`, `subir` (default track internal, status draft); 28 tablet copies, sha256-equal
  to sources; pipeline copies all three formats; setup section in the publication doc. Checks:
  `fastlane lanes` lists 3 lanes; generator exit 0; revisar.py exit 0; no key or report in git.
- First AAB built by the parent on 2026-09-28 from main: `app/build/outputs/bundle/release/
  constanza-0.1.16.aab`, jarsigner verified, signer `CN=Constanza, OU=jjrapps` (upload key),
  package com.jjrapps.constanza, versionCode 17, versionName 0.1.16.

## Next step

Owner: create the app, upload that AAB by hand, create the service account + key. Then T2.
