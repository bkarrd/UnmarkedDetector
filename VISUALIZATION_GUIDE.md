# 📡 Wizualizacja Bounding Boxów ALPR - Instrukcja Integracji

## Przegląd Rozwiązania

Projekt zawiera kompletne rozwiązanie do wizualizacji detekcji tablic rejestracyjnych w aplikacji Android ALPR. System rysuje bounding boxy (prostokątne ramki) wokół wykrytych tablic na podglądzie kamery w czasie rzeczywistym.

### Komponenty

| Komponent | Lokalizacja | Opis |
|-----------|-----------|------|
| **GraphicOverlay** | `ui/common/GraphicOverlay.kt` | Custom View do rysowania bounding boxów |
| **CoordinateTransformer** | `util/CoordinateTransformer.kt` | Transformacja współrzędnych między przestrzeniami |
| **VisualizationManager** | `detection/VisualizationManager.kt` | Menedżer danych wizualizacyjnych |
| **DetectionVisualizationHelper** | `detection/DetectionVisualizationHelper.kt` | Helper transformacji danych |
| **DetectionVisualizationData** | `domain/model/DetectionVisualizationData.kt` | Model danych do wizualizacji |
| **ImagePreprocessor** | `detection/ImagePreprocessor.kt` | (Ulepszona) metoda `cropRegionSafe()` |

---

## 🔄 Pipeline Transformacji Współrzędnych

```
┌─────────────────────────────────────────────────────────────┐
│ Model TFLite (224x224)                                      │
│ Wyjście: [x_center, y_center, width, height] ∈ [0.0, 1.0] │
└──────────────┬──────────────────────────────────────────────┘
               │ (znormalizowane YOLO)
               ▼
┌─────────────────────────────────────────────────────────────┐
│ ImageProxy (np. 1440x1080, rotacja 90°)                     │
│ Współrzędne w pikselach klatki z kamery                     │
└──────────────┬──────────────────────────────────────────────┘
               │ (transformacja + rotacja)
               ▼
┌─────────────────────────────────────────────────────────────┐
│ PreviewView / Ekran telefonu (np. 1080x2340)                │
│ Współrzędne gotowe do rysowania na ekranie                  │
└─────────────────────────────────────────────────────────────┘
```

### Obsługiwane Rotacje

- **0°** - Bez rotacji (portret)
- **90°** - Obrót w prawo (krajobraz)
- **180°** - Obrót o 180°
- **270°** - Obrót w lewo

---

## 📋 Instrukcja Integracji

### 1. **Layout XML** (`fragment_main_driving.xml`)

GraphicOverlay jest już dodany do layoutu jako overlay na PreviewView:

```xml
<FrameLayout
    android:id="@+id/camera_preview_container"
    android:layout_width="0dp"
    android:layout_height="0dp">
    
    <androidx.camera.view.PreviewView
        android:id="@+id/preview_view"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
    
    <com.example.unmarkeddetector.ui.common.GraphicOverlay
        android:id="@+id/graphic_overlay"
        android:layout_width="match_parent"
        android:layout_height="match_parent" />
</FrameLayout>
```

### 2. **MainDrivingFragment.kt** - Integracja GraphicOverlay

```kotlin
override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
    super.onViewCreated(view, savedInstanceState)
    binding = FragmentMainDrivingBinding.bind(view)

    binding?.apply {
        previewView.implementationMode = 
            androidx.camera.view.PreviewView.ImplementationMode.COMPATIBLE
        detectionCoordinator.attachPreview(previewView)
        
        // 👈 Podpij GraphicOverlay do detektora wizualizacji
        detectionCoordinator.attachGraphicOverlay(graphicOverlay)
        
        // ... reszta kodu
    }
}

override fun onDestroyView() {
    // Odłącz GraphicOverlay
    detectionCoordinator.detachGraphicOverlay()
    detectionCoordinator.detachPreview()
    binding = null
    super.onDestroyView()
}
```

### 3. **DetectionCoordinator.kt** - Injekcja VisualizationManager

```kotlin
@Singleton
class DetectionCoordinator @Inject constructor(
    // ... inne dependencje
    private val visualizationManager: VisualizationManager,
    @ApplicationScope private val applicationScope: CoroutineScope
) {
    // ...
    
    fun attachGraphicOverlay(overlay: GraphicOverlay) {
        visualizationManager.attachGraphicOverlay(overlay)
    }
    
    fun detachGraphicOverlay() {
        visualizationManager.detachGraphicOverlay()
    }
}
```

---

## 🎨 Konfiguracja Wizualizacji

### Podstawowe Użycie - Rysowanie Bounding Boksów

```kotlin
val graphicOverlay: GraphicOverlay = binding.graphicOverlay

// Utwórz box
val detectionBox = GraphicOverlay.DetectionBox(
    rect = Rect(100, 200, 400, 500),  // piksele ekranu
    confidence = 0.95f,
    isAlert = false,
    label = "PLATE: AB12CD",
    color = Color.GREEN
)

// Dodaj do overlaya
graphicOverlay.addDetectionBox(detectionBox)

// Lub ustaw wiele boxów naraz
graphicOverlay.setDetectionBoxes(listOf(detectionBox1, detectionBox2))
```

### Konfiguracja Stylizacji

```kotlin
val config = GraphicOverlay.GraphicConfig(
    boundingBoxStrokeWidth = 4f,      // grubość linii
    boundingBoxColor = Color.GREEN,    // kolor normalny
    alertBoxColor = Color.RED,         // kolor alarmu
    textSize = 32f,                    // rozmiar tekstu label'u
    textColor = Color.WHITE,           // kolor tekstu
    confidenceThreshold = 0.5f,        // próg widoczności
    enableConfidenceLabel = true,      // pokaż procent pewności
    labelBackground = true,            // tło pod labelami
    labelBackgroundColor = Color.BLACK  // kolor tła
)

graphicOverlay.config = config
```

### Wyczyszczenie Visualizacji

```kotlin
// Wyczyść wszystkie bounding boxy
graphicOverlay.clearDetectionBoxes()

// Lub przez VisualizationManager
visualizationManager.clearVisualization()
```

---

## 🔧 Transformacja Współrzędnych - Przykłady

### Przykład 1: Transformacja z YOLO na Ekran

```kotlin
// Wyjście modelu TFLite
val xCenter = 0.5f   // środek X (znormalizowany)
val yCenter = 0.6f   // środek Y (znormalizowany)
val width = 0.3f     // szerokość (znormalizowana)
val height = 0.2f    // wysokość (znormalizowana)

// Transformuj bezpośrednio na ekran
val screenRect = CoordinateTransformer.yoloToScreenPixels(
    xCenter = xCenter,
    yCenter = yCenter,
    width = width,
    height = height,
    frameWidth = 1440,        // szerokość ImageProxy
    frameHeight = 1080,       // wysokość ImageProxy
    screenWidth = 1080,       // szerokość PreviewView
    screenHeight = 2340,      // wysokość PreviewView
    rotationDegrees = 90      // rotacja z ImageProxy
)

// screenRect.toAndroidRect() → Rect do rysowania
```

### Przykład 2: Transformacja z Pikseli ImageProxy

```kotlin
// Region tablicy w pikselach ImageProxy
val regionRect = Rect(300, 200, 600, 400)

// Transformuj na piksele ekranu
val frameCoordRect = CoordinateTransformer.CoordinateRect(
    left = regionRect.left,
    top = regionRect.top,
    right = regionRect.right,
    bottom = regionRect.bottom,
    space = CoordinateTransformer.CoordinateSpace.FRAME_PIXELS
)

val screenCoordRect = CoordinateTransformer.frameToScreenPixels(
    frameRect = frameCoordRect,
    frameWidth = 1440,
    frameHeight = 1080,
    screenWidth = 1080,
    screenHeight = 2340,
    rotationDegrees = 90
)

val finalRect = screenCoordRect.toAndroidRect()
```

### Przykład 3: Za Pomocą Helper'a (ZALECANE)

```kotlin
val screenRect = DetectionVisualizationHelper.transformRegionToScreen(
    regionRect = plateRect,
    imageProxy = imageProxy,
    screenWidth = previewView.width,
    screenHeight = previewView.height
)

val box = DetectionVisualizationHelper.createDetectionBox(
    screenRect = screenRect,
    confidence = 0.92f,
    isAlert = false,
    label = "AB12CD"
)

graphicOverlay.addDetectionBox(box)
```

---

## 🔒 Bezpieczne Wycinanie dla ML Kit

### Nowa Metoda: `ImagePreprocessor.cropRegionSafe()`

```kotlin
val imagePreprocessor: ImagePreprocessor = // ...

val croppedBitmap = imagePreprocessor.cropRegionSafe(
    fullBitmap = fullBitmap,
    regionRect = plateRect,
    minDimension = 32,        // minimum wymiar dla ML Kit
    paddingPercent = 0.25f    // 25% padding gdy wymiar < 40px
)

if (croppedBitmap != null) {
    // Wytnięty obraz ma gwarantowany rozmiar ≥ 32px
    val text = mlKit.recognize(croppedBitmap)
} else {
    // Wymiary zbyt małe - ignoruj
    Log.w(TAG, "Plate too small after padding, skipping OCR")
}
```

### Logika Wewnętrzna

1. Jeśli szerokość **LUB** wysokość < 40px → dodaj 25% padding z każdej strony
2. Po paddingu: jeśli wciąż < 32px → zwróć `null`
3. Clamp do granic bitmapy
4. Zwróć wycięty fragment lub `null`

---

## 📊 Przepływ Danych w Aplikacji

```
PlateFrameAnalyzer
    ├─> Analiza klatki (ImageProxy)
    ├─> Detekcja regionów tablicy
    ├─> Transformacja współrzędnych
    │   └─> VisualizationManager.publishVisualizationData()
    │       └─> GraphicOverlay.addDetectionBox()  ✅ RYSOWANIE
    │
    └─> OCR (ML Kit)
        └─> Wynik detekcji
```

---

## 🎯 Implementacja w Istniejącym Kodzie

### Jeśli Chcesz Dodać Wizualizację do PlateFrameAnalyzer

```kotlin
class PlateFrameAnalyzer(
    // ... inne parametry
    private val visualizationManager: VisualizationManager,
    private val previewWidth: () -> Int,
    private val previewHeight: () -> Int
) : ImageAnalysis.Analyzer {
    
    override fun analyze(image: ImageProxy) {
        // ... istniejący kod analizy
        
        // Po wykryciu tablicy:
        val screenRect = DetectionVisualizationHelper.transformRegionToScreen(
            plateRegion.rect,
            image,
            previewWidth(),
            previewHeight()
        )
        
        if (screenRect != null) {
            val box = DetectionVisualizationHelper.createDetectionBox(
                screenRect = screenRect,
                confidence = plateRegion.score,
                isAlert = false,
                label = "PLATE"
            )
            
            graphicOverlay.addDetectionBox(box)
        }
    }
}
```

---

## ⚠️ Ważne Uwagi

1. **Thread-Safety**: GraphicOverlay jest thread-safe - możesz wywoływać `addDetectionBox()` z dowolnego wątku.

2. **Rysowanie**: Rysowanie odbywają się w metodzie `onDraw()` - nie blokuje głównego wątku dzięki `postInvalidate()`.

3. **Pamięć**: Bounding boxy są przechowywane w `MutableList` - wyczyść je regularnie aby uniknąć wycieku pamięci.

4. **Rotacja**: CoordinateTransformer automatycznie obsługuje rotację z `ImageProxy.imageInfo.rotationDegrees`.

5. **Aspect Ratio**: Transformer automatycznie obsługuje mismatch aspect ratio między ImageProxy a ekranem.

---

## 🧪 Testing - Jak Zweryfikować Działanie

1. **Uruchom aplikację** na urządzeniu (Galaxy S22 lub podobne)
2. **Przejdź do widoku jazdy** (MainDrivingFragment)
3. **Rozpocznij detekcję** (przycisk START)
4. **Skieruj kamerę** na tablice rejestracyjne
5. **Obserwuj**:
   - ✅ Zielone ramki pojawią się wokół wykrytych tablic
   - ✅ Label będzie pokazywać procent pewności
   - ✅ Ramki będą śledzić tablicę w czasie rzeczywistym (smooth animation)
   - ✅ Ramki będą poprawnie rozmieszczone (bez przesunięcia/rotacji)

---

## 📌 TODO - Przyszłe Ulepszenia

- [ ] Animacja ramek (fade in/out)
- [ ] Różne kolory dla tracked/new detections
- [ ] Wyświetlanie tekstu tablicy po rozpoznaniu
- [ ] Statystyki wizualne (FPS, liczba tablic/sekundę)
- [ ] Opcja wyłączenia wizualizacji dla wydajności
- [ ] Kalibracja transformacji współrzędnych na podstawie kalibracji kamery

---

## 📞 Debugging

Jeśli bounding boxy **nie pojawiają się**:

```kotlin
// 1. Sprawdź czy GraphicOverlay jest podpięty
Log.d("DEBUG", "GraphicOverlay width: ${graphicOverlay.width}, height: ${graphicOverlay.height}")

// 2. Sprawdź czy detekcja działa
Log.d("DEBUG", "Detected plates: ${scanResult.detectedPlates}")

// 3. Sprawdź transformację współrzędnych
Log.d("DEBUG", "Screen rect: $screenRect")

// 4. Sprawdź czy overlay ma wymiary
binding.graphicOverlay.post {
    Log.d("DEBUG", "Overlay ready: ${binding.graphicOverlay.width}x${binding.graphicOverlay.height}")
}
```

---

## 📝 Historia Zmian

- **v1.0** (2024-05-20): Inicjalna implementacja
  - GraphicOverlay custom view
  - CoordinateTransformer ze wsparciem rotacji
  - VisualizationManager
  - Integracja z MainDrivingFragment

---

Powodzenia! 🚀
