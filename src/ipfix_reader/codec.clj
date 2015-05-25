(ns ipfix-reader.codec
  (:use [gloss io core])
  (:gen-class))

(defcodec message-header
  [:version-number :uint16
   :length :uint16
   :export-time :uint32
   :sequence-number :uint32
   :observation-domain-id :uint32])

(defcodec set-header
  [:set-id :uint16
   :length :uint16])

(defcodec template-header
  [:template-id :uint16
   :field-count :uint16])

(defcodec option-template-header
  [:template-id :uint16
   :field-count :uint16
   :scope-field-count :uint16])

(defcodec field-specifier-info-element
  [:information-element-id :uint16])

(defcodec field-specifier
  [:field-length :uint16])

(defcodec field-specifier-with-enterprise
  [:field-length :uint16
   :enterprise-number :uint32])

(def codec-to-keyword {:1 :ubyte
                       :2 :uint16
                       :4 :uint32
                       :8 :uint64
                       :16 [:uint64 :uint64]
                       :65535 :str})
                       ;:65535 (header
                       ;        :ubyte
                       ;        (fn [x]
                       ;          (if-not (= x 255)
                       ;            (compile-frame [:str-val x])
                       ;            (header :uint16
                       ;                    (fn [y]
                       ;                      (compile-frame [:str-val y])) nil)))
                       ;        nil)})
