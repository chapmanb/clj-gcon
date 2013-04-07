(ns gcon.core
  "Top level API for working with remote filestores."
  (:require [gcon.file :as file]
            [gcon.client :as client]))

(def get-client client/get-client)
(def list-dirs file/list-dirs)
(def list-files file/list-files)
(def get-file file/get-file)
(def put-file file/put-file)
