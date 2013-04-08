(ns gcon.client
  "Establish a connection with a remote service for file management."
  (:use [clojure.java.io])
  (:require [clojure.string :as string]
            [blend.galaxy.core :as galaxy]
            [clj-genomespace.core :as gs]
            [org.jclouds.blobstore2 :as blobstore]))

(defrecord RemoteClient [type conn username server])

(defmulti get-client
  "Retrieve a remote client using authentication credentials
   creds is a map containing the :type of connection to establish
   and client specific authentication details."
  (fn [creds]
    (:type creds)))

(def ^{:private true} gs-default-server "http://www.genomespace.org/")

(defmethod get-client :gs
  ^{:doc "Retrieve a GenomeSpace client connection"}
  [creds]
  (let [{:keys [username password client url allow-offline?]} creds
        gs-client (cond
                   (and client (gs/logged-in? client)) client
                   (and username password) (try (gs/get-client username :password password)
                                                (catch Exception e
                                                  (when-not allow-offline?
                                                    (throw e))))
                   :else nil)
        username (when gs-client
                   (gs/get-username gs-client))]
    (RemoteClient. :gs gs-client username (or url gs-default-server))))

(defmethod get-client :galaxy
  ^{:doc "Retrieve a Galaxy client connection."}
  [creds]
  (let [{:keys [url api-key client allow-offline?]} creds
        galaxy-client (cond
                       (not (nil? client)) client
                       (and url api-key) (galaxy/get-client url api-key)
                       :else nil)
        user-info (try (galaxy/get-user-info galaxy-client)
                       (catch Exception e
                         (when-not allow-offline?
                           (throw e))))
        username (when user-info
                   (or (:username user-info) (:email user-info)))]
    (RemoteClient. :galaxy (when user-info galaxy-client) username url)))

(defmethod get-client :blobstore
  ^{:doc "Connection to a jClouds compatible S3-style key-value blobstore"}
  [creds]
  (let [{:keys [username password provider]} creds]
    (let [jclient (blobstore/blobstore (name provider) username password)]
      (RemoteClient. :blobstore jclient username provider))))
