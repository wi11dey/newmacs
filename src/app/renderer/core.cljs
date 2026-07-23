(ns app.renderer.core
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [garden.core :as garden]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@uiw/react-codemirror" :default CodeMirror]
            ["@replit/codemirror-minimap" :refer [showMinimap]]
            ["dockview-react" :refer [DockviewReact themeDark]]
            ["@tauri-apps/api/core" :refer [invoke]]
            ["@tauri-apps/api/event" :refer [listen]]
            [app.renderer.emacs :refer [with-emacs]]))

(enable-console-print!)

(listen "cljs"
        (fn [event]
          (js/console.log "Requested to run:" (.-payload event))))

(with-emacs
  (require 'url)

  (message "Newmacs connected on port %d" newmacs-port) ; `newmacs-port' is set on emacs by the server

  (defun newmacs-send (form)
    (let ((url-request-method "POST")
          (url-request-extra-headers
           '(("Content-Type" . "text/plain; charset=utf-8")))
          (url-request-data (encode-coding-string (prin1-to-string form) 'utf-8)))
      (url-retrieve-synchronously (concat "http://localhost:" newmacs-port))))

  (defun newmacs-new-buffer ())

  (add-hook 'after-change-major-mode-hook 'newmacs-new-buffer))

(defonce code (r/atom "console.log('hello from cljs');"))

(defn user-chrome []
  (let [css (r/atom "")]
    (-> (invoke "read_user_chrome")
        (.then (fn [source]
                 (when (seq source)
                   (try
                     (reset! css (apply garden/css (reader/read-string source)))
                     (catch :default error
                       (js/console.error "Could not parse ~/.newmacs" error))))))
        (.catch #(js/console.error "Could not load ~/.newmacs" %)))
    (fn []
      [:style#user-chrome @css])))

(def minimap-extension
  (.compute showMinimap #js ["doc"]
            (fn [_state]
              #js {:create (fn [_view]
                             (let [dom (js/document.createElement "div")]
                               #js {:dom dom}))
                   :displayText "blocks"
                   :showOverlay "always"})))

(defn editor []
  [:> CodeMirror
   {:className  "code-editor"
    :height     "100%"
    :width      "100%"
    :value      @code
    :extensions #js [minimap-extension]
    :onChange   (fn [value _ev]
                  (reset! code value))}])

(defn root []
  [:<>
   [user-chrome]
   [:> DockviewReact
    {:components #js {"editor" #(r/as-element [editor])
                      "editor2" #(r/as-element [editor])}
     :onReady (fn [event]
                (.addPanel ^js (.-api event)
                           #js {:id "editor"
                                :component "editor"
                                :title "Editor"})
                (.addPanel ^js (.-api event)
                           #js {:id "editor2"
                                :component "editor2"
                                :title "Editor2"}))}]])

(defn start! []
  (-> (js/document.getElementById "app")
      (rdc/create-root)
      (rdc/render [root])))
