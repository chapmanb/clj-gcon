(ns gcon.file
  "List, retrieve and push files from a remote filestore."
  (:use [clojure.java.io])
  (:require [clojure.string :as string]
            [me.raynes.fs :as fs]
            [blend.galaxy.core :as galaxy]
            [clj-genomespace.core :as gs]
            [org.jclouds.blobstore2 :as blobstore]))

;; ## Download

(def ^{:doc "Provide list of files currently under download."
       :private true}
  download-queue (atom #{}))

(defn url->dir
  [url]
  (string/replace (.getHost (as-url url)) "." "_"))

(defn- download-to-local
  "Generalized remote download to local cache directory.
   download-fn takes the client, remote name and local name, and
   handles the download step for a specific remote service."
  [fname rclient to-fileinfo-fn download-fn cache-dir]
  (let [finfo (to-fileinfo-fn fname)
        local-file (str (file cache-dir (url->dir (:server rclient)) (:local-stub finfo)))
        local-dir (str (fs/parent local-file))]
    (when-not (fs/exists? local-file)
      (when-not (fs/exists? local-dir)
        (fs/mkdirs local-dir))
      (when-not (contains? @download-queue local-file)
        (swap! download-queue conj local-file)
        (try
          (download-fn rclient finfo local-file)
          (finally
            (swap! download-queue disj local-file)))))
    local-file))

(defmulti get-file
  "Retrieve files by name, transparently handling remote files."
  (fn [fname & args]
    (let [parts (string/split fname #":" 2)]
      (when (= 2 (count parts))
        (keyword (first parts))))))

(defmethod get-file :gs
  ^{:doc "Retrieve a file from GenomeSpace to the local cache"}
  [fname rclient cache-dir]
  (letfn [(fileinfo-gs [file-id]
            (let [remote-name (second (string/split file-id #":" 2))]
              {:dirname (str (fs/parent remote-name))
               :fname (fs/base-name remote-name)
               :local-stub (if (.startsWith remote-name "/")
                             (subs remote-name 1)
                             remote-name)}))
          (download-gs [rclient file-info out-file]
            (gs/download (:conn rclient) (:dirname file-info)
                         (:fname file-info) out-file))]
    (download-to-local fname rclient fileinfo-gs download-gs cache-dir)))

(defn- split-galaxy-id
  [file-id]
  (when file-id
    (-> file-id
        (string/split #":" 2)
        second
        (string/split #"/"))))

(defmethod get-file :galaxy
  ^{:doc "Retrieve a file from Galaxy to the local cache"}
  [fname rclient cache-dir]
  (letfn [(fileinfo-galaxy [file-id]
            (let [[history-id ds-id] (split-galaxy-id file-id)
                  ds (galaxy/get-dataset-by-id (:conn rclient) history-id ds-id)
                  safe-username (string/replace (:username rclient) " " "-")]
              {:local-stub (str (file safe-username history-id (:name ds)))
               :ds ds}))
          (download-galaxy [rclient file-info out-file]
            (galaxy/download-dataset (:conn rclient) (:ds file-info) out-file))]
    (download-to-local fname rclient fileinfo-galaxy download-galaxy cache-dir)))

(defmethod get-file :default
  ^{:doc "Get local file: no-op, just return the file."}
  [fname _]
  fname)

;; ## List

(defmulti list-dirs
  "List directories available on the remote server. Returns map of directory
   :id and display :name."
  (fn [rclient & args]
    (:type rclient)))

(defmethod list-dirs :gs
  ^{:doc "Retrieve available directories from GenomeSpace under the parent directory."}
  [rclient dirname]
  (map (fn [x] {:id x :name x})
       (gs/list-dirs (:conn rclient) dirname)))

(defmethod list-dirs :galaxy
  ^{:doc "Retrieve available histories from Galaxy connection."}
  [rclient _]
  (galaxy/list-histories (:conn rclient)))

(defmethod list-dirs :blobstore
  ^{:doc "Retrieve top level containers/buckets for a key value store"}
  [rclient _]
  (seq (blobstore/containers (:conn rclient))))

(defmulti list-files
  "List files in a remote directory of a specified type."
  (fn [rclient & args]
    (:type rclient)))

(defmethod list-files :gs
  ^{:doc "List available files in GenomeSpace directory by type."}
  [rclient rdir ftype]
  (let [remote-dir (or (:id rdir) ".")]
    (concat
     (map (fn [finfo]
            {:id (str "gs:" (:dirname finfo) "/" (:name finfo))
             :tags (remove nil?
                           [(first (drop 3 (string/split (:dirname finfo) #"/" 4)))])
             :folder (:dirname finfo)
             :filename (:name finfo)
             :size (:size finfo)
             :created-on (:date finfo)})
          (gs/list-files (:conn rclient) remote-dir (name ftype)))
     (mapcat #(list-files rclient % ftype) (list-dirs rclient remote-dir)))))

(defmethod list-files :galaxy
  ^{:doc "List available files from a Galaxy history."}
  [rclient hid ftype]
  (let [history-id (if (contains? hid :id) (:id hid) hid)
        history-name (or (:name hid) "")]
    (->> (galaxy/get-datasets-by-type (:conn rclient) ftype :history-id history-id)
         (remove #(:deleted %))
         (map (fn [ds]
                {:id (str "galaxy:" (:history-id ds) "/" (:id ds))
                 :tags [history-name]
                 :folder history-name
                 :filename (:name ds)
                 :size (:file-size ds)
                 :created-on nil})))))

(defmethod list-files :blobstore
  ^{:doc "List available items within a container/bucket"}
  [rclient container _]
  (seq (blobstore/blobs (:conn rclient) (:id container))))

(defmethod list-files :default
  ^{:doc "Retrieval of pre-downloaded files in our local cache."}
  [_ dir-info ftype])

;; ## Upload

(defmulti put-file
  "Upload a file to a remote repository."
  (fn [rclient & args]
    (:type rclient)))

(defmethod put-file :gs
  ^{:doc "Push file to GenomeSpace in the specified upload directory."}
  [rclient local-file params]
  (let [remote-dir (str (fs/file (fs/parent (last (string/split (:input-file params) #":" 2)))
                                 (:tag params)))]
    (gs/upload (:conn rclient) remote-dir local-file)
    remote-dir))

(defmethod put-file :galaxy
  ^{:doc "Push file to the current Galaxy history, using a remotely available URL."}
  [rclient local-file params]
  (let [host-info (:host-info params)
        history-id (first (split-galaxy-id (:input-file params)))
        provide-url ((:expose-fn params) local-file (:server rclient))]
    (galaxy/upload-to-history (:conn rclient) provide-url
                              (get params :dbkey :hg19)
                              (:file-type params)
                              :history-id history-id
                              :display-name (fs/base-name local-file))))

(defmethod put-file :blobstore
  ^{:doc "Push to a remote blobstore within a supplied container"}
  [rclient local-file params]
  (let [{:keys [container metadata]} params
        cur-blob (blobstore/blob (fs/base-name local-file)
                                 :payload (file local-file)
                                 :metadata metadata)]
    (when-not (blobstore/container-exists? (:conn rclient) container)
      (blobstore/create-container (:conn rclient) container))
    (blobstore/put-blob (:conn rclient) container cur-blob)))
