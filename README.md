# VoiceCut MP3 — CHK Cut

Application Android native Java destinée à enregistrer rapidement des prises de voix de jeu vidéo, couper automatiquement le silence, ajuster la coupe à la milliseconde et exporter un vrai MP3 localement.

## Caractéristiques

- Android natif Java, minSdk 21, compileSdk/targetSdk 34.
- Enregistrement PCM 16 bits mono haute qualité (44,1 kHz, repli 48 kHz si nécessaire).
- Waveform réelle, zoom tactile, lecture, marqueurs début/fin et réglages ±10/±100 ms.
- Détection de voix locale adaptative : RMS, dBFS, estimation du bruit ambiant, hystérésis et marges configurables.
- Traitement optionnel doux : DC offset, gate adaptatif, normalisation, amplification contrôlée, limiteur et fades.
- Export MP3 LAME réel en 96/128/192/256 kb/s, 44,1 kHz mono.
- Profils CHEIKH, YVANE et NELVYN.
- Numérotation automatique par catégorie.
- Sauvegarde dans Music/VoiceCut/<Profil>/ via MediaStore sur Android 10+.
- Historique local, écoute, renommage, modification, partage et suppression.
- Aucun cloud, aucune publicité, aucun compte et aucune API externe à l'exécution.

## Compiler

```bash
chmod +x gradlew
./gradlew assembleDebug
```

APK : `app/build/outputs/apk/debug/app-debug.apk`

Le workflow `.github/workflows/android.yml` compile automatiquement ce même APK et le publie comme Artifact GitHub Actions.

## Dépendance MP3

Le projet utilise `com.github.naman14:TAndroidLame:1.1`, wrapper Android open source de LAME. La dépendance est téléchargée uniquement à la compilation ; l'application encode ensuite les MP3 entièrement en local.
