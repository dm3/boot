(ns boot.pom
  (:refer-clojure :exclude [name])
  (:require 
   [clojure.java.io      :as io]
   [boot.pod             :as pod]
   [boot.file            :as file]
   [boot.util            :as util]
   [boot.xml             :as xml]
   [clojure.xml          :refer [parse]]
   [clojure.zip          :refer [xml-zip]]
   [clojure.data.zip.xml :refer [attr text xml-> xml1->]])
  (:import
   [java.util     Properties]
   [java.io       ByteArrayInputStream]
   [java.util.jar JarEntry JarOutputStream]))

;;; elements ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(xml/decelems
  artifactId connection description dependencies dependency exclusion
  exclusions developerConnection enabled groupId id license licenses
  modelVersion name email project scope tag url scm version comments
  developer developers)

;;; private ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn pom-xml-parse-string [xml-str]
  (let [z   (-> xml-str .getBytes ByteArrayInputStream. parse xml-zip)
        gid (util/guard (xml1-> z :groupId text))
        aid (util/guard (xml1-> z :artifactId text))]
    {:project     (util/guard (if (= gid aid) (symbol aid) (symbol gid aid)))
     :version     (util/guard (xml1-> z :version text))
     :description (util/guard (xml1-> z :description text))
     :url         (util/guard (xml1-> z :url text))
     :scm         {:url (util/guard (xml1-> z :scm :url text))
                   :tag (util/guard (xml1-> z :scm :tag text))}}))

(defn pom-xml-parse
  ([jarpath]
   (pom-xml-parse jarpath nil))
  ([jarpath pompath]
   (pom-xml-parse-string (pod/pom-xml jarpath pompath))))

(defn pom-xml [{p :project
                v :version
                d :description
                l :license
                {su :url
                 st :tag
                 sc :connection
                 sd :developerConnection} :scm
                ds :developers
                u :url
                deps :dependencies
                :as env}]
  (let [[g a] (util/extract-ids p)
        ls    (map (fn [[name url]] {:name name :url url}) l)]
    (project
      :xmlns              "http://maven.apache.org/POM/4.0.0"
      :xmlns:xsi          "http://www.w3.org/2001/XMLSchema-instance"
      :xsi:schemaLocation "http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd"
      (modelVersion "4.0.0")
      (groupId      g)
      (artifactId   a)
      (version      v)
      (name         a)
      (description  d)
      (url          u)
      (licenses
        (for [{ln :name lu :url lc :comments} ls]
          (license
            (url      lu)
            (name     ln)
            (comments lc))))
      (when (or su st sc sd)
        (scm
          (when sc (connection sc))
          (when sd (developerConnection sd))
          (when su (url su))
          (when st (tag st))))
      (when-let [ds (seq ds)]
        (developers
          (for [[n e] ds]
            (developer
              (name n)
              (email e)))))
      (dependencies
        (for [[p v & {es :exclusions s :scope}] deps
              :let [[g a] (util/extract-ids p)]]
          (dependency
            (groupId    g)
            (artifactId a)
            (version    v)
            (scope      (or s "compile"))
            (exclusions
              (for [p es :let [[g a] (util/extract-ids p)]]
                (exclusion
                  (groupId    g)
                  (artifactId a))))))))))

;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn spit-pom! [xmlpath proppath {:keys [project version] :as env}]
  (let [[gid aid] (util/extract-ids project)
        prop      (doto (Properties.)
                    (.setProperty "groupId"    gid)
                    (.setProperty "artifactId" aid)
                    (.setProperty "version"    version))
        xmlfile   (doto (io/file xmlpath) io/make-parents)
        propfile  (doto (io/file proppath) io/make-parents)]
    (spit xmlfile (pr-str (pom-xml env)))
    (with-open [s (io/output-stream propfile)]
      (.store prop s (str gid "/" aid " " version " property file")))))
