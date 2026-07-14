(ns app.main.core
  (:require ["electron" :refer [app BrowserWindow]]))

(defn main []
  (.on app "window-all-closed" #(.quit app))
  (.on app "ready" #(.loadURL (BrowserWindow.
                               (clj->js {:width 800
                                         :height 600
                                         :webPreferences
                                         {:nodeIntegration true
                                          :contextIsolation false}}))
                              (str "file://" js/__dirname "/public/index.html"))))
