/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.solr.core;

import org.apache.solr.common.util.NamedList;
import org.apache.solr.schema.IndexSchema;

/**
 * Stores a core's configuration in the form of a SolrConfig and IndexSchema. Immutable.
 *
 * @see ConfigSetService
 */
public class ConfigSet {

  private final String name;

  private final SolrConfig solrConfig;
  private volatile IndexSchema schema;

  private final SchemaSupplier schemaSupplier;

  private final NamedList<?> properties;

  public ConfigSet(
      String name,
      SolrConfig solrConfig,
      SchemaSupplier indexSchemaSupplier,
      NamedList<?> properties) {
    this.name = name;
    this.solrConfig = solrConfig;
    this.schemaSupplier = indexSchemaSupplier;
    schema = schemaSupplier.get(true);
    this.properties = properties;
  }

  public String getName() {
    return name;
  }

  public SolrConfig getSolrConfig() {
    return solrConfig;
  }

  /**
   * @param forceFetch get a fresh value and not cached value
   */
  public IndexSchema getIndexSchema(boolean forceFetch) {
    if (forceFetch) schema = schemaSupplier.get(true);
    return schema;
  }

  public IndexSchema getIndexSchema() {
    return schema;
  }

  public NamedList<?> getProperties() {
    return properties;
  }

  /**
   * Provide a Schema object on demand We want IndexSchema Objects to be lazily instantiated because
   * when a configset is created the {@link SolrResourceLoader} associated with it is not associated
   * with a core So, we may not be able to update the core if the schema classes are updated
   */
  interface SchemaSupplier {
    IndexSchema get(boolean forceFetch);
  }
}
