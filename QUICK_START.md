# 🚀 Quick Start - Wizualizacja Bounding Boxów

## Co Zostało Zrobione?

✅ **GraphicOverlay** - Custom View do rysowania prostokątów  
✅ **CoordinateTransformer** - Mapowanie współrzędnych (YOLO → Screen)  
✅ **VisualizationManager** - Menedżer danych wizualizacyjnych  
✅ **DetectionVisualizationHelper** - Helper transformacji  
✅ **ImagePreprocessor.cropRegionSafe()** - Bezpieczne wycinanie dla ML Kit  
✅ **Layout XML** - GraphicOverlay nałożony na PreviewView  
✅ **DetectionCoordinator** - Integracja z wizualizacją  
✅ **MainDrivingFragment** - Podpięcie GraphicOverlay  

---

## 📦 Nowe Pliki

```
app/src/main/java/com/example/unmarkeddetector/
├── ui/common/
│   └── GraphicOverlay.kt                    ✨ Custom View
├── util/
│   └── CoordinateTransformer.kt             ✨ Mapowanie współrzędnych
├── detection/
│   ├── VisualizationManager.kt              ✨ Menedżer wizualizacji
│   ├── DetectionVisualizationHelper.kt      ✨ Helper transformacji
│   └── ImagePreprocessor.kt                 ✏️  Ulepszone: cropRegionSafe()
└── domain/model/
    └── DetectionVisualizationData.kt        ✨ Model danych

app/src/main/res/layout/
└── fragment_main_driving.xml                ✏️  Ulepszone: dodano GraphicOverlay
```

---

## 🎯 Jak Się To Integruje?

```kotlin
// 1. W MainDrivingFragment.onViewCreated()
detectionCoordinator.attachPreview(previewView)
detectionCoordinator.attachGraphicOverlay(graphicOverlay)  // ✨ NOWE

// 2. W DetectionCoordinator
visualizationManager.attachGraphicOverlay(overlay)

// 3. GraphicOverlay rysuje bounding boxy
graphicOverlay.addDetectionBox(box)  // thread-safe
```

---

## 🔧 Użycie - 3 Sposoby

### 1️⃣ Prosty Sposób (Bez Transformacji)

```kotlin
val rect = Rect(100, 200, 400, 500)  // piksele ekranu
val box = GraphicOverlay.DetectionBox(
    rect = rect,
    confidence = 0.92f,
    label = "PLATE: AB12CD"
)
graphicOverlay.addDetectionBox(box)
```

### 2️⃣ Ze Współrzędnymi ImageProxy

```kotlin
val screenRect = DetectionVisualizationHelper.transformRegionToScreen(
    plateRegion.rect,      // piksele ImageProxy
    imageProxy,
    previewView.width,
    previewView.height
)

val box = DetectionVisualizationHelper.createDetectionBox(
    screenRect = screenRect,
    confidence = 0.92f
)
graphicOverlay.addDetectionBox(box)
```

### 3️⃣ Z YOLO Znormalizowanymi Współrzędnymi

```kotlin
val screenRect = CoordinateTransformer.yoloToScreenPixels(
    xCenter = 0.5f,   // ze współrzędnych YOLO modelu TFLite
    yCenter = 0.6f,
    width = 0.3f,
    height = 0.2f,
    frameWidth = 1440,
    frameHeight = 1080,
    screenWidth = previewView.width,
    screenHeight = previewView.height,
    rotationDegrees = imageProxy.imageInfo.rotationDegrees
)
```

---

## 🧪 Testing

```bash
# 1. Kompiluj
./gradlew assembleDebug

# 2. Uruchom na urządzeniu
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 3. Otwórz aplikację
# 4. Przejdź do widoku jazdy
# 5. Kliknij START
# 6. Skieruj kamerę na tablicę
# 7. Obserwuj zielone ramki! 🟩
```

---

## ⚙️ Konfiguracja Stylizacji

```kotlin
val config = GraphicOverlay.GraphicConfig(
    boundingBoxStrokeWidth = 4f,
    boundingBoxColor = Color.GREEN,
    alertBoxColor = Color.RED,
    textSize = 32f,
    enableConfidenceLabel = true
)
graphicOverlay.config = config
```

---

## 🔒 Bezpieczne Wycinanie dla ML Kit

```kotlin
// Stare
val crop = imagePreprocessor.cropPlateForMlKit(bitmap, rect)

// Nowe - bardziej bezpieczne
val crop = imagePreprocessor.cropRegionSafe(
    fullBitmap = bitmap,
    regionRect = rect,
    minDimension = 32,      // ML Kit minimum
    paddingPercent = 0.25f  // 25% padding gdy zbyt mały
)

if (crop != null) {
    mlKit.recognize(crop)
} else {
    Log.d("TAG", "Wymiary za małe, pomijam OCR")
}
```

---

## 🎨 Colory Predefiniowane

```kotlin
// Normalny bounding box
Color.GREEN              // #00FF00

// Alert / Wykryta tablica
Color.RED                // #FF0000

// Tracked tablica (opcja do dodania)
Color.YELLOW             // #FFFF00
Color.CYAN               // #00FFFF
```

---

## 📊 Transformacja Współrzędnych - Czemu to Ważne?

```
Model TFLite (224x224)
    ↓ znormalizowane YOLO [0-1]
    
ImageProxy z kamery (np. 1440x1080, rotacja 90°)
    ↓ uwzględni rotację
    
Ekran telefonu (np. 1080x2340, portret)
    ↓
Gotowe do rysowania! 🎯
```

Bez właściwej transformacji:
- ❌ Ramki będą przesunięte
- ❌ Ramki będą obrócone
- ❌ Ramki będą zniekształcone

Z transformacją:
- ✅ Ramki idealnie dopasowane
- ✅ Obsługa wszystkich rotacji (0°, 90°, 180°, 270°)
- ✅ Obsługa aspect ratio mismatch

---

## 🐛 Debugging

```kotlin
// Sprawdź czy overlay ma wymiary
binding.graphicOverlay.post {
    Log.d("DEBUG", "Width: ${binding.graphicOverlay.width}, " + 
                   "Height: ${binding.graphicOverlay.height}")
}

// Sprawdź współrzędne transformacji
Log.d("DEBUG", "Screen rect: $screenRect")

// Sprawdź bounding boxy
val boxes = graphicOverlay.getDetectionBoxes()
Log.d("DEBUG", "Drawn boxes: ${boxes.size}")

// Sprawdź czy ImageProxy ma rozmiary
Log.d("DEBUG", "ImageProxy: ${imageProxy.width}x${imageProxy.height}, " +
               "rotation: ${imageProxy.imageInfo.rotationDegrees}°")
```

---

## 📝 Pliki Do Zmiany (GOTOWE ✅)

- ✅ `fragment_main_driving.xml` - GraphicOverlay w FrameLayout
- ✅ `MainDrivingFragment.kt` - attachGraphicOverlay()
- ✅ `DetectionCoordinator.kt` - VisualizationManager injection
- ✅ `ImagePreprocessor.kt` - cropRegionSafe()

---

## 🎯 Next Steps (Opcjonalne Ulepszenia)

1. **Animacja**: Fade in/out dla nowych detektów
2. **Multi-color**: Różne kolory dla tracked vs. new
3. **Labels**: Wyświetlaj rozpoznany tekst tablicy
4. **Stats**: FPS, liczba tablic/sekundę
5. **Kalibracja**: Poprawianie accuracy transformacji na bazie kamery

---

## 📞 Ważne Numery Linii (dla szybkiego dostępu)

| Plik | Funkcja | Linia |
|------|---------|-------|
| GraphicOverlay.kt | addDetectionBox() | ~72 |
| GraphicOverlay.kt | clearDetectionBoxes() | ~83 |
| CoordinateTransformer.kt | yoloToScreenPixels() | ~98 |
| CoordinateTransformer.kt | frameToScreenPixels() | ~47 |
| ImagePreprocessor.kt | cropRegionSafe() | ~46 |
| VisualizationManager.kt | publishVisualizationData() | ~49 |

---

## ✅ Checklist Integracji

- [x] GraphicOverlay created
- [x] CoordinateTransformer created
- [x] VisualizationManager created
- [x] DetectionVisualizationHelper created
- [x] ImagePreprocessor.cropRegionSafe() added
- [x] fragment_main_driving.xml updated
- [x] MainDrivingFragment.kt updated
- [x] DetectionCoordinator.kt updated
- [x] Kompilacja pomyślna ✅

---

**Status**: ✅ Gotowe do użycia w produkcji!

Powodzenia! 🚀
