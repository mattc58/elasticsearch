/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the "Elastic License
 * 2.0", the "GNU Affero General Public License v3.0 only", and the "Server Side
 * Public License v 1"; you may not use this file except in compliance with, at
 * your election, the "Elastic License 2.0", the "GNU Affero General Public
 * License v3.0 only", or the "Server Side Public License, v 1".
 */

package org.elasticsearch.ingest.common;


import org.elasticsearch.ingest.AbstractProcessor;
import org.elasticsearch.ingest.ConfigurationUtils;
import org.elasticsearch.ingest.IngestDocument;
import org.elasticsearch.ingest.Processor;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.IOException;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import static org.elasticsearch.ingest.ConfigurationUtils.newConfigurationException;

/**
 * Processor that serializes a string-valued field into a
 * map of maps.
 */
public final class XmlProcessor extends AbstractProcessor {

    public static final String TYPE = "xml";
    private static final String STRICT_XML_PARSING_PARAMETER = "strict_xml_parsing";

    private final String field;
    private final String targetField;
    private final boolean addToRoot;
    private final ConflictStrategy addToRootConflictStrategy;
    private final boolean allowDuplicateKeys;
    private final boolean strictXmlParsing;

    XmlProcessor(
        String tag,
        String description,
        String field,
        String targetField,
        boolean addToRoot,
        ConflictStrategy addToRootConflictStrategy,
        boolean allowDuplicateKeys
    ) {
        this(tag, description, field, targetField, addToRoot, addToRootConflictStrategy, allowDuplicateKeys, true);
    }

    XmlProcessor(
        String tag,
        String description,
        String field,
        String targetField,
        boolean addToRoot,
        ConflictStrategy addToRootConflictStrategy,
        boolean allowDuplicateKeys,
        boolean strictXmlParsing
    ) {
        super(tag, description);
        this.field = field;
        this.targetField = targetField;
        this.addToRoot = addToRoot;
        this.addToRootConflictStrategy = addToRootConflictStrategy;
        this.allowDuplicateKeys = allowDuplicateKeys;
        this.strictXmlParsing = strictXmlParsing;
    }

    public String getField() {
        return field;
    }

    public String getTargetField() {
        return targetField;
    }

    boolean isAddToRoot() {
        return addToRoot;
    }

    public ConflictStrategy getAddToRootConflictStrategy() {
        return addToRootConflictStrategy;
    }

    private static Map<String, Object> xmlToMap(Document d) {
        d.normalize();
        Node root = d.getDocumentElement();
        return Map.of(root.getNodeName(), xmlToMap(root));
    }

    private static Map<String, Object> xmlToMap(Node root) {
        Map<String, Object> map = new HashMap<>();
        NodeList childNodes = root.getChildNodes();

        for (int i = 0; i < childNodes.getLength(); i++) {
            Node current = childNodes.item(i);
            if (current.getNodeType() == Node.TEXT_NODE) {
                map.put("", current.getNodeValue());
            } else if (current.getNodeType() == Node.ELEMENT_NODE) {
                if (current.hasChildNodes()) {
                    Map<String, Object> childMap = xmlToMap(current);
                    if (childMap.size() == 1 && childMap.containsKey("")) {
                        map.put(current.getNodeName(), childMap.get(""));
                    } else {
                        map.put(current.getNodeName(), childMap);
                    }
                }
            }
        }

        if (root.hasAttributes()) {
            NamedNodeMap attributes = root.getAttributes();
            for (int i = 0; i < attributes.getLength(); i++) {
                Node attribute = attributes.item(i);
                map.put(attribute.getNodeName(), attribute.getNodeValue());
            }
        }

        return map;
    }

    public static Object apply(Object fieldValue, boolean allowDuplicateKeys, boolean strictXmlParsing) {
        try {
            Document doc = null;
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            InputSource inputSource = new InputSource(new StringReader(fieldValue == null ? "null" : fieldValue.toString()));

            doc = dBuilder.parse(inputSource);
            Map<String, Object> map = xmlToMap(doc);

            Object value = map;

            //            if (token == XContentParser.Token.VALUE_NULL) {
//                value = null;
//            } else if (token == XContentParser.Token.VALUE_STRING) {
//                value = parser.text();
//            } else if (token == XContentParser.Token.VALUE_NUMBER) {
//                value = parser.numberValue();
//            } else if (token == XContentParser.Token.VALUE_BOOLEAN) {
//                value = parser.booleanValue();
//            } else if (token == XContentParser.Token.START_OBJECT) {
//                value = parser.map();
//            } else if (token == XContentParser.Token.START_ARRAY) {
//                value = parser.list();
//            } else if (token == XContentParser.Token.VALUE_EMBEDDED_OBJECT) {
//                throw new IllegalArgumentException("cannot read binary value");
//            }
//            if (strictXmlParsing) {
//                String errorMessage = Strings.format(
//                    "The input %s is not valid XML and the %s parameter is true",
//                    fieldValue,
//                    STRICT_XML_PARSING_PARAMETER
//                );
//                /*
//                 * If strict XML parsing is disabled, then once we've found the first token then we move on. For example for the string
//                 * "123 \"foo\"" we would just return the first token, 123. However, if strict parsing is enabled (which it is by default),
//                 * then we check to see whether there are any more tokens at this point. We expect the next token to be null. If there is
//                 * another token or if the parser blows up, then we know we had invalid JSON and we alert the user with an
//                 * IllegalArgumentException.
//                 */
//                try {
//                    token = parser.nextToken();
//                } catch (IllegalArgumentException e) {
//                    throw new IllegalArgumentException(errorMessage, e);
//                }
//                if (token != null) {
//                    throw new IllegalArgumentException(errorMessage);
//                }
//            }
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        } catch (SAXException | ParserConfigurationException e) {
            // MATT: log and investigate
            throw new IllegalArgumentException(e);
        }
    }

    public static void apply(
        Map<String, Object> ctx,
        String fieldName,
        boolean allowDuplicateKeys,
        ConflictStrategy conflictStrategy,
        boolean strictXmlParsing
    ) {
        Object value = apply(ctx.get(fieldName), allowDuplicateKeys, strictXmlParsing);
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) value;
            if (conflictStrategy == ConflictStrategy.MERGE) {
                recursiveMerge(ctx, map);
            } else {
                ctx.putAll(map);
            }
        } else {
            throw new IllegalArgumentException("cannot add non-map fields to root of document");
        }
    }

    public static void recursiveMerge(Map<String, Object> target, Map<String, Object> from) {
        for (String key : from.keySet()) {
            if (target.containsKey(key)) {
                Object targetValue = target.get(key);
                Object fromValue = from.get(key);
                if (targetValue instanceof Map && fromValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> targetMap = (Map<String, Object>) targetValue;
                    @SuppressWarnings("unchecked")
                    Map<String, Object> fromMap = (Map<String, Object>) fromValue;
                    recursiveMerge(targetMap, fromMap);
                } else {
                    target.put(key, fromValue);
                }
            } else {
                target.put(key, from.get(key));
            }
        }
    }

    @Override
    public IngestDocument execute(IngestDocument document) throws Exception {
        if (addToRoot) {
            apply(document.getSourceAndMetadata(), field, allowDuplicateKeys, addToRootConflictStrategy, strictXmlParsing);
        } else {
            document.setFieldValue(targetField, apply(document.getFieldValue(field, Object.class), allowDuplicateKeys, strictXmlParsing));
        }
        return document;
    }

    @Override
    public String getType() {
        return TYPE;
    }

    public enum ConflictStrategy {
        REPLACE,
        MERGE;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static ConflictStrategy fromString(String conflictStrategy) {
            return ConflictStrategy.valueOf(conflictStrategy.toUpperCase(Locale.ROOT));
        }
    }

    public static final class Factory implements Processor.Factory {

        @Override
        public XmlProcessor create(
            Map<String, Processor.Factory> registry,
            String processorTag,
            String description,
            Map<String, Object> config
        ) throws Exception {
            String field = ConfigurationUtils.readStringProperty(TYPE, processorTag, config, "field");
            String targetField = ConfigurationUtils.readOptionalStringProperty(TYPE, processorTag, config, "target_field");
            boolean addToRoot = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, "add_to_root", false);
            boolean allowDuplicateKeys = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, "allow_duplicate_keys", false);
            String conflictStrategyString = ConfigurationUtils.readOptionalStringProperty(
                TYPE,
                processorTag,
                config,
                "add_to_root_conflict_strategy"
            );
            boolean hasConflictStrategy = conflictStrategyString != null;
            if (conflictStrategyString == null) {
                conflictStrategyString = ConflictStrategy.REPLACE.name();
            }
            ConflictStrategy addToRootConflictStrategy;
            try {
                addToRootConflictStrategy = ConflictStrategy.fromString(conflictStrategyString);
            } catch (IllegalArgumentException e) {
                throw newConfigurationException(
                    TYPE,
                    processorTag,
                    "add_to_root_conflict_strategy",
                    "conflict strategy [" + conflictStrategyString + "] not supported, cannot convert field."
                );
            }

            if (addToRoot && targetField != null) {
                throw newConfigurationException(
                    TYPE,
                    processorTag,
                    "target_field",
                    "Cannot set a target field while also setting `add_to_root` to true"
                );
            }
            if (addToRoot == false && hasConflictStrategy) {
                throw newConfigurationException(
                    TYPE,
                    processorTag,
                    "add_to_root_conflict_strategy",
                    "Cannot set `add_to_root_conflict_strategy` if `add_to_root` is false"
                );
            }
            boolean strictParsing = ConfigurationUtils.readBooleanProperty(TYPE, processorTag, config, STRICT_XML_PARSING_PARAMETER, true);

            if (targetField == null) {
                targetField = field;
            }

            return new XmlProcessor(
                processorTag,
                description,
                field,
                targetField,
                addToRoot,
                addToRootConflictStrategy,
                allowDuplicateKeys,
                strictParsing
            );
        }
    }
}
