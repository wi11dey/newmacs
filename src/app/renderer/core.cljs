(ns app.renderer.core
  (:require [cljs.js :as cljs]
            [cljs.reader :as reader]
            [clojure.string :as str]
            [garden.core :as garden]
            [reagent.core :as r]
            [reagent.dom.client :as rdc]
            ["@uiw/react-codemirror" :default CodeMirror]
            ["@replit/codemirror-minimap" :refer [showMinimap]]
            ["flexlayout-react" :refer [Layout Model]]
            ["@tauri-apps/api/core" :refer [invoke]]
            ["@tauri-apps/api/event" :refer [listen]]
            [app.renderer.emacs :refer [with-emacs]])
  (:require-macros [app.renderer.eval :refer [analyze-ns]]))

(enable-console-print!)

(let [compile-state (cljs/empty-state
                     #(assoc-in %
                                [:cljs.analyzer/namespaces 'app.renderer.core]
                                (analyze-ns)))]
  (listen "newmacs-eval"
          (fn [event]
            (cljs/eval-str compile-state
                           (.-payload event)
                           nil
                           {:eval cljs/js-eval
                            :ns 'app.renderer.core}
                           (fn [{:keys [error]}]
                             (when error
                               (js/console.error "Could not evaluate form" error)))))))

(with-emacs
  (require 'url)

  (message "Newmacs connected on port %d" newmacs-port) ; `newmacs-port' is set on emacs by the server

  (defun newmacs-eval (form)
    (let ((url-request-method "POST")
          (url-request-extra-headers
           '(("Content-Type" . "text/plain; charset=utf-8")))
          (url-request-data (encode-coding-string (prin1-to-string form) 'utf-8))
          url-show-status)
      (url-retrieve-synchronously (format "http://localhost:%d/" newmacs-port))))

  (newmacs-eval '(js/console.error "Hello"))

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

(def windows
  (.fromJson Model
             (clj->js
              {:global {:tabEnableClose false}
               :borders []
               :layout {:type "row"
                        :children [{:type "tabset"
                                    :children [{:type "tab"
                                                :id "editor"
                                                :name "Editor"
                                                :component "editor"}
                                               {:type "tab"
                                                :id "editor2"
                                                :name "Editor2"
                                                :component "editor"}]}]}})))

(defn root []
  [:<>
   [user-chrome]
   [:> Layout {:model windows
               :factory (fn [^js node]
                          (case (.getComponent node)
                            "editor" (r/as-element [editor])
                            nil))}]])

(defn start! []
  (-> (js/document.getElementById "app")
      (rdc/create-root)
      (rdc/render [root])))

