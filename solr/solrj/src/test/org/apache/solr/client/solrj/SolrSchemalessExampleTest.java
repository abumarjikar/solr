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
package org.apache.solr.client.solrj;

import java.io.ByteArrayInputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import org.apache.commons.io.file.PathUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.solr.client.solrj.impl.HttpClientUtil;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.JavaBinRequestWriter;
import org.apache.solr.client.solrj.impl.JavaBinResponseParser;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.util.Utils;
import org.apache.solr.util.ExternalPaths;
import org.junit.BeforeClass;
import org.junit.Test;

public class SolrSchemalessExampleTest extends SolrExampleTestsBase {

  @BeforeClass
  public static void beforeClass() throws Exception {
    Path tempSolrHome = createTempDir();
    // Schemaless renames schema.xml -> schema.xml.bak, and creates + modifies conf/managed-schema,
    // which violates the test security manager's rules, which disallow writes outside the build
    // dir, so we copy the example/example-schemaless/solr/ directory to a new temp dir where writes
    // are allowed.
    final Path sourceFile = ExternalPaths.SERVER_HOME.resolve("solr.xml");
    Files.copy(sourceFile, tempSolrHome.resolve("solr.xml"));
    Path collection1Dir = tempSolrHome.resolve("collection1");
    Files.createDirectories(collection1Dir);
    PathUtils.copyDirectory(ExternalPaths.DEFAULT_CONFIGSET, collection1Dir);
    Properties props = new Properties();
    props.setProperty("name", "collection1");
    OutputStreamWriter writer = null;
    try {
      writer =
          new OutputStreamWriter(
              PathUtils.newOutputStream(collection1Dir.resolve("core.properties"), false),
              StandardCharsets.UTF_8);
      props.store(writer, null);
    } finally {
      if (writer != null) {
        try {
          writer.close();
        } catch (Exception ignore) {
        }
      }
    }
    createAndStartJetty(tempSolrHome);
  }

  @Test
  public void testArbitraryJsonIndexing() throws Exception {
    SolrClient client = getSolrClient();
    client.deleteByQuery("*:*");
    client.commit();
    assertNumFound("*:*", 0); // make sure it got in

    // two docs, one with uniqueKey, another without it
    String json = "{\"id\":\"abc1\", \"name\": \"name1\"} {\"name\" : \"name2\"}";
    HttpClient httpClient = getHttpClient();
    HttpPost post = new HttpPost(getCoreUrl() + "/update/json/docs");
    post.setHeader("Content-Type", "application/json");
    post.setEntity(
        new InputStreamEntity(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), -1));
    HttpResponse response =
        httpClient.execute(post, HttpClientUtil.createNewHttpClientRequestContext());
    Utils.consumeFully(response.getEntity());
    assertEquals(200, response.getStatusLine().getStatusCode());
    client.commit();
    assertNumFound("*:*", 2);
  }

  @Test
  public void testFieldMutating() throws Exception {
    SolrClient client = getSolrClient();
    client.deleteByQuery("*:*");
    client.commit();
    assertNumFound("*:*", 0); // make sure it got in
    // two docs, one with uniqueKey, another without it
    String json =
        "{\"name one\": \"name\"} "
            + "{\"name  two\" : \"name\"}"
            + "{\"first-second\" : \"name\"}"
            + "{\"x+y\" : \"name\"}"
            + "{\"p%q\" : \"name\"}"
            + "{\"p.q\" : \"name\"}"
            + "{\"a&b\" : \"name\"}";
    HttpClient httpClient = getHttpClient();
    HttpPost post = new HttpPost(getCoreUrl() + "/update/json/docs");
    post.setHeader("Content-Type", "application/json");
    post.setEntity(
        new InputStreamEntity(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)), -1));
    HttpResponse response = httpClient.execute(post);
    assertEquals(200, response.getStatusLine().getStatusCode());
    client.commit();
    List<String> expected =
        Arrays.asList("name_one", "name__two", "first-second", "a_b", "p_q", "p.q", "x_y");
    HashSet<String> set = new HashSet<>();
    QueryResponse rsp = assertNumFound("*:*", expected.size());
    for (SolrDocument doc : rsp.getResults()) set.addAll(doc.getFieldNames());
    for (String s : expected) {
      assertTrue(s + " not created " + rsp, set.contains(s));
    }
  }

  @Override
  public SolrClient createNewSolrClient() {
    HttpSolrClient.Builder httpSolrClientBuilder =
        new HttpSolrClient.Builder(getBaseUrl())
            .withDefaultCollection(DEFAULT_TEST_CORENAME)
            .allowMultiPartPost(random().nextBoolean());
    if (random().nextBoolean()) {
      httpSolrClientBuilder
          .withRequestWriter(new JavaBinRequestWriter())
          .withResponseParser(new JavaBinResponseParser());
    }
    return httpSolrClientBuilder.build();
  }
}
