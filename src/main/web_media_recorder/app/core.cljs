;; Based on:
;; https://github.com/mdn/web-dictaphone/blob/gh-pages/scripts/app.js

(ns web-media-recorder.app.core
  (:require [goog.string :as gstr]))

(defn log
  ([x] (.log js/console x))
  ([x y] (.log js/console x y)))

(defn qs [x]
  (.querySelector js/document x))

;; HTML elements

(def record       (qs ".record"))
(def stop         (qs ".stop"))
(def sound-clips  (qs ".sound-clips"))
(def canvas       (qs ".visualizer"))
(def main-section (qs ".main-controls"))

;; disable stop button while not recording
(set! (.-disabled stop) true)

;; Canvas

(def canvas-ctx (.getContext canvas "2d"))
(set! js/audio_ctx nil)

;; State

(defonce chunks (atom []))

;; Utils

(defn draw [{:keys [analyser data-array buffer-length]}]
  (let [width  500
        height 60]
    (println "analyser:" analyser)
    (.requestAnimationFrame js/window draw)
    (.getByteTimeDomainData analyser data-array)
    (set! (.-fillStyle canvas-ctx) "#c8c8c8")
    (.fillRect canvas-ctx 0 0 width height)
    (set! (.-lineWidth canvas-ctx) 2)
    (set! (.-strokeStyle canvas-ctx) "black")
    (.beginPath canvas-ctx)
    (let [slice-width (/ (* width 1.0) buffer-length)]
        (dotimes [i buffer-length]
          (let [v (/ (aget data-array i) 128.0)
                y (* v (/ height 2))
                x (* i slice-width)]
            (if (= i 0)
              (.moveTo canvas-ctx x y)
              (.lineTo canvas-ctx x y))))
        (.lineTo canvas-ctx width (/ height 2))
        (.stroke canvas-ctx))))

(defn visualize [stream]
  (println "js/audio_ctx" js/audio_ctx)
  (when-not js/audio_ctx
    (set! js/audio_ctx (js/AudioContext.)))
  (let [source   (.createMediaStreamSource js/audio_ctx stream)
        analyser (.createAnalyser js/audio_ctx)]
    (set! (.-fftSize analyser) 2048)
    (let [buffer-length (.-frequencyBinCount analyser)
          data-array    (js/Uint8Array. buffer-length)]
      (.connect source analyser)
      (draw {:analyser analyser
             :data-array data-array
             :buffer-length buffer-length}))))

(defn stop-media-recorder []
  (let [tc   (clj->js {"type" "audio/ogg; codecs=opus"})
        blob (js/Blob. @chunks tc)
        url  (.-URL js/window)
        aurl (.createObjectURL url blob)
        cc   (.createElement js/document "article")
        cl   (.createElement js/document "p")
        aud  (.createElement js/document "audio")
        db   (.createElement js/document "button")]
    (log "media recorder stopped")

    (set! js/clipName (js/prompt "Enter a name for your sound clip"))
    (.add (.-classList cc) "clip")
    (set! (.-controls aud) true)
    (set! (.-textContent db) "Delete")
    (set! (.-className db) "delete")

    (if (gstr/isEmptyOrWhitespace js/clipName)
      (set! (.-textContent cl) "My unnamed clip")
      (set! (.-textContent cl) js/clipName))

    (.appendChild cc aud)
    (.appendChild cc cl)
    (.appendChild cc db)
    (.appendChild sound-clips cc)

    (reset! chunks [])
    (set! (.-src aud) aurl)

    (set! (.-onclick db) (fn [e]
                           (let [et (.-target e)
                                 pn (.-parentNode et)
                                 ppn (.-parentNode pn)]
                             (.removeChild ppn pn))))))

(defn handle-stream [stream]
  (let [mr (js/MediaRecorder. stream)
        rh #(do (.start mr)
                (log (.-state mr))
                (log "recorder started")
                (set! (.. record -style -background) "red")
                (set! (.. record -style -color) "black")
                (set! (.-disabled stop) false)
                (set! (.-disabled record) true))
        sh #(do (.stop mr)
                (log (.-state mr))
                (log "recorder stopped")
                (set! (.. record -style -background) "")
                (set! (.. record -style -color) "")
                (set! (.-disabled stop) true)
                (set! (.-disabled record) false))
        ud #(swap! chunks conj (.-data %))]
    (visualize stream)
    (set! (.-ondataavailable mr) ud)
    (set! (.-onclick record) rh)
    (set! (.-onclick stop) sh)
    (set! (.-onstop mr) stop-media-recorder)))

;; Render

(defn render []
  (let [nav (.-mediaDevices js/navigator)
        gum (.-getUserMedia nav)
        cs  (clj->js {:audio true})]
    (if gum
      (-> (.getUserMedia nav cs)
          (.then handle-stream)
          (.catch (fn [err] (log err))))
      (log "getUserMedia not supported on your browser!"))))

(defn ^:export main []
  (render))

(defn ^:dev/after-load reload! []
  (.reload js/location)
  (render))
