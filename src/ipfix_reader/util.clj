(ns ipfix-reader.util
  (:use [gloss core io])
  (:gen-class))

(defn is-template-set?
  "Determines whether the given set-id is defined as a template set"
  [^Integer set-id]
  (if (== set-id 2) true false))

(defn is-option-template-set?
  "Determines whether the given set-id is defined as an option template set"
  [^Integer set-id]
  (if (== set-id 3) true false))

(defn is-reserved-set?
  "Determines if the given set-id is within a portion of reserved space"
  [^Integer set-id]
  (if (and (> set-id 3) (< set-id 256)) true false))

(defn is-data-set?
  "Determines if the given set-id is a properly validated data set"
  [^Integer set-id]
  (if (> set-id 255) true false))

(defn skip-bytes
  "Skips an arbitrary amount of bytes from an input stream in a greedy fashion"
  [stream num-bytes]
  (loop [bytes-to-skip num-bytes]
    (if-not (= bytes-to-skip 0)
      (recur (- bytes-to-skip (.skip stream bytes-to-skip))))))

(defn parse-any
  "Takes a codec, its size, and a file stream to decode the stream at its current point"
  ([codec byte-stream] 
     (apply hash-map (decode codec byte-stream)))
  ([codec ^Integer size stream]
     (let [tmp-buf (byte-array size)]
       (.read stream tmp-buf)
       (apply hash-map (decode codec tmp-buf)))))

(defn parse-str
  "Parses an individual string based on the RFC for IPFix dynamically-sized values"
  [ipfix-buf]
  (let [{str-len :str-len} (parse-any (compile-frame [:str-len :ubyte]) 1 ipfix-buf)]
    (if (= str-len 255)
      (let [{str-len :str-len} (parse-any (compile-frame [:str-len :uint16]) 2 ipfix-buf)]
        [(+ 3 str-len) (parse-any (compile-frame [:str-val (repeat str-len :ubyte)]) str-len ipfix-buf)])
      [(+ 1 str-len) (parse-any (compile-frame [:str-val (repeat str-len :ubyte)]) str-len ipfix-buf)])))

(defn parse-message-header
  "Parses the global message header from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/message-header 16 ipfix-buf)) 

(defn parse-set-header
  "Parses an individual set header from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/set-header 4 ipfix-buf))

(defn parse-template-header
  "Parses an individual template header from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/template-header 4 ipfix-buf))

(defn parse-option-template-header
  "Parses an individual option template header from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/option-template-header 6 ipfix-buf))

(defn parse-field-specifier-info-element
  "Parses the information element out of the field specifier from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/field-specifier-info-element 2 ipfix-buf))

(defn parse-field-specifier
  "Parses an individual field specifier length from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/field-specifier 2 ipfix-buf))

(defn parse-field-specifier-with-enterprise
  "Parses an individual field specifier with an enterprise value from the ipfix file"
  [ipfix-buf]
  (parse-any ipfix-reader.codec/field-specifier-with-enterprise 6 ipfix-buf))
