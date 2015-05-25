(ns ipfix-reader.parser
  (:require [ipfix-reader codec])
  (:use [gloss core io] [clojure.java io] [ipfix-reader util])
  (:gen-class))

(defn parse-record
  "Parses a single record from the buffer given the codec list supplied"
  [ipfix-buf codec-list]
  (loop [codecs codec-list
         total-bytes-read 0]
    (if (.isEmpty codecs)
      total-bytes-read
      (let [codec (first codecs)]
        (if (= codec :str)
          (let [[bytes-read str] (parse-str ipfix-buf)]
            (prn str)
            (recur (rest codecs) (+ total-bytes-read bytes-read)))
          (let [str (parse-any (codec 1) (codec 0) ipfix-buf)]
            (prn str)
            (recur (rest codecs) (+ total-bytes-read (codec 0)))))))))

(defn parse-records
  "Parses a set of records from the buffer given the codec supplied"
  [ipfix-buf records-size codec-list]
  (loop [total-bytes-read 0]
    (if (< total-bytes-read records-size)
      (let [bytes-read (parse-record ipfix-buf codec-list)]
        (recur (+ total-bytes-read bytes-read))))))

(defn parse-template
  "Parses through a single ipfix template and returns a map containing the set-id and the gloss
   structure able to parse its binary format"
  [ipfix-buf template-type]
  (let [{template-id :template-id field-count :field-count}
        (if (= template-type :option)
          (parse-option-template-header ipfix-buf)
          (parse-template-header ipfix-buf))
        init-bytes (if (= template-type :option) 6 4)]
    (loop [curr-field 0
           bytes-read init-bytes
           frame-list []
           struct-list []
           struct-size 0]
      (if (>= curr-field field-count)
        [bytes-read {(keyword (str template-id))
                     (if (.isEmpty struct-list)
                       frame-list
                       (conj frame-list [struct-size (compile-frame struct-list)]))}]
        (let [{info-elem :information-element-id} (parse-field-specifier-info-element ipfix-buf)
              byte-incr-count (if (> info-elem 32768) 8 4)
              {field-len :field-length} (if (= byte-incr-count 8)
                                          (parse-field-specifier-with-enterprise ipfix-buf)
                                          (parse-field-specifier ipfix-buf))
              field-frame ((keyword (str field-len)) ipfix-reader.codec/codec-to-keyword)]
          (if (= field-frame :str)
            (recur (inc curr-field)
                   (+ bytes-read byte-incr-count)
                   (if (.isEmpty struct-list)
                     (conj frame-list :str)
                     (conj frame-list [struct-size (compile-frame struct-list)] :str))
                   []
                   0)
            (recur (inc curr-field)
                   (+ bytes-read byte-incr-count)
                   frame-list
                   (conj struct-list (keyword (str curr-field)) field-frame)
                   (+ struct-size field-len))))))))

(defn parse-templates
  "Parses an arbitrary set of templates until the bytes read match the original size provided"
  [ipfix-buf template-size template-type]
  (loop [total-bytes-read 0
         template-map {}]
    (if (< total-bytes-read template-size)
      (let [[bytes-read codec-map] (parse-template ipfix-buf template-type)]
        (recur (+ bytes-read total-bytes-read) (merge template-map codec-map)))
      template-map)))

(defn parse-message
  "Parses through a single ipfix message and all of its set elements"
  [ipfix-buf message-length templates]
  (let [set-header-len 4]
    (loop [bytes-read 16
           template-map templates]
      (if (>= bytes-read message-length)
        (if (> bytes-read message-length)
          (str "ERROR: Read " bytes-read " bytes and should have been of length " message-length)
          template-map)
        (let [set-hdr (parse-set-header ipfix-buf)
              {set-id :set-id len :length} set-hdr]
          (cond
           (is-template-set? set-id) (do
                                       (println "template set")
                                       (recur (+ bytes-read len)
                                              (merge template-map
                                                     (parse-templates
                                                      ipfix-buf (- len set-header-len) nil))))
           (is-option-template-set? set-id) (do
                                              (println "option template set")
                                              (recur (+ bytes-read len)
                                                     (merge template-map
                                                            (parse-templates
                                                             ipfix-buf (- len set-header-len)
                                                             :option))))
           (is-reserved-set? set-id) (do
                                       (println "WARN: Found reserved Set ID:" set-id)
                                       (skip-bytes ipfix-buf (- len set-header-len))
                                       (recur (+ bytes-read len) template-map))
           (is-data-set? set-id) (do
                                   (println "data set with set-id:" set-id)
                                   (let [codec ((keyword (str set-id)) template-map)]
                                     (if codec
                                       (do (parse-records ipfix-buf (- len set-header-len) codec)
                                           (recur (+ bytes-read len) template-map))
                                       (do
                                         (println "ERROR: Could not find a parse formula for Set ID:" set-id)
                                         (System/exit -1)))))
           :else (println "ERROR: Could not determine the Set ID:" set-id)))))))

(defn parse-ipfix
  "Parses an ipfix file from the location provided"
  [^String ipfix-file]
  (let [ipfix-buf (input-stream ipfix-file)]
    (loop [mesg-hdr (parse-message-header ipfix-buf)
           templates {}]
      (if-not (= (:length mesg-hdr) 0)
        (let [template-map (parse-message ipfix-buf (:length mesg-hdr) templates)]
          (recur (parse-message-header ipfix-buf) template-map))))))
