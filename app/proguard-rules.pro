# R8 keep rules for :app (isMinifyEnabled + isShrinkResources, see build.gradle.kts).
#
# This file is deliberately EMPTY OF RULES, and that is a verified result rather than an
# assumption. The first release build this project ever produced was checked in three ways
# before concluding that nothing needs keeping:
#
#   1. Build.      `:app:assembleRelease` succeeded with no rules at all.
#   2. Artifact.   The release DEX was inspected for every reflection-adjacent symbol this app
#                  actually depends on:
#                    - Room resolves its generated implementation by name, and
#                      `com.jjrapps.constanza.core.data.AppDatabase_Impl` is present unobfuscated
#                      (androidx.room ships the consumer rule that keeps it).
#                    - HiltWorkerFactory keys its factory map on worker class-name STRINGS; all
#                      five (`ReconcileWorker`, `MidnightSweepWorker`, `ReminderFireWorker`,
#                      `AnswerWorker`, `SnoozeWorker`) survive as literals, and the classes
#                      themselves stay unobfuscated.
#                    - Manifest-declared components (MainActivity, the five receivers,
#                      ConstanzaApplication) stay unobfuscated via the AAPT-generated rules.
#                    - kotlinx-serialization needs no rule here because every call site passes an
#                      explicit `BackupFile.serializer()` rather than the reflective
#                      `serializer(typeOf<T>())` path, and the generated `$$serializer` classes
#                      survive regardless. JSON key names are string literals inside the
#                      descriptor, so obfuscating the Kotlin properties cannot rename them.
#   3. Runtime.    The signed release APK was installed on an API 37 emulator and driven through
#                  onboarding, habit creation, answering, a real AlarmManager reminder firing into
#                  ReminderFireWorker, a notification action dispatching through ActionReceiver
#                  into AnswerWorker, the Progress screen, and a full backup export + import
#                  round-trip. No crash, no ClassNotFoundException, no SerializationException.
#
# Two rules that are commonly added by reflex were tested and are NOT needed:
#
#   - `-keepattributes SourceFile,LineNumberTable` / `-renamesourcefileattribute SourceFile`.
#     R8 8.x already emits `sourceFile` records and line-number mappings into
#     `app/build/outputs/mapping/release/mapping.txt` by default. Verified by feeding an
#     obfuscated frame to `cmdline-tools/latest/bin/retrace`, which recovered the class, method,
#     file and line exactly. Adding the rule would only put real source names back into the APK.
#   - Any blanket `-keep class com.jjrapps.constanza.** { *; }`. That would defeat the shrinking
#     this build type exists for and would hide genuine breakage instead of surfacing it.
#
# ADD RULES ONLY WITH EVIDENCE. When a release-only failure appears, retrace the stack against
# `mapping.txt`, add the narrowest rule that fixes that exact symptom, and record the library and
# the symptom here — do not paste a defensive blob.
