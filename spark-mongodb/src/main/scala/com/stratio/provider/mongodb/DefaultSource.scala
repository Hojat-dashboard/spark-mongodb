/*
 *  Licensed to STRATIO (C) under one or more contributor license agreements.
 *  See the NOTICE file distributed with this work for additional information
 *  regarding copyright ownership. The STRATIO (C) licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package com.stratio.provider.mongodb

import com.mongodb.MongoCredential
import com.stratio.provider.DeepConfig._
import org.apache.spark.sql.sources._
import org.apache.spark.sql.SQLContext
import MongodbConfig._

/**
 * Allows creation of MongoDB based tables using
 * the syntax CREATE TEMPORARY TABLE ... USING com.stratio.deep.mongodb.
 * Required options are detailed in [[com.stratio.provider.mongodb.MongodbConfig]]
 */
class DefaultSource extends RelationProvider {

  override def createRelation(
                               sqlContext: SQLContext,
                               parameters: Map[String, String]): BaseRelation = {

    /** We will assume hosts are provided like 'host:port,host2:port2,...' */
    val host = parameters
      .getOrElse(Host, notFound[String](Host))
      .split(",").toList

    val database = parameters.getOrElse(Database, notFound(Database))

    val collection = parameters.getOrElse(Collection, notFound(Collection))

    val samplingRatio = parameters
      .get(SamplingRatio)
      .map(_.toDouble).getOrElse(DefaultSamplingRatio)

    val properties :Map[String, Any] =
      Map(Host -> host, Database -> database, Collection -> collection , SamplingRatio -> samplingRatio)

    val optionalProperties: List[String] = List(Credentials,SSLOptions)
    val finalMap = (properties /: optionalProperties){    //TODO improve code
      case (properties,Credentials) =>
        /** We will assume credentials are provided like 'user,database,password;user,database,password;...' */
        val credentialInput = parameters.getOrElse(Credentials, " ")
        if(credentialInput.compareTo(" ")!=0){
          val credentials= credentialInput
            .split(";")
            .map(credential => credential.split(",")).toList
            .map(credentials => MongoCredential.createCredential(credentials(0), credentials(1), credentials(2).toCharArray))
          properties.+(Credentials -> credentials)
        } else properties
      case (properties,SSLOptions) =>
        /** We will assume ssloptions are provided like '/path/keystorefile,keystorepassword,/path/truststorefile,truststorepassword' */
        val ssloptionInput = parameters.getOrElse(SSLOptions, " ")
        if(ssloptionInput.compareTo(" ")!=0) {
          val ssloption = ssloptionInput.split(",")
          val ssloptions = MongodbSSLOptions(Some(ssloption(0)), Some(ssloption(1)), ssloption(2), Some(ssloption(3)))
          properties.+(SSLOptions -> ssloptions)
        }
        else properties
    }

    MongodbRelation(
      MongodbConfigBuilder()
        .apply(finalMap)
        .build())(sqlContext)

  }

}