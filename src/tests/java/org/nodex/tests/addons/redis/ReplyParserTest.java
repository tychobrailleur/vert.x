/*
 * Copyright 2011 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.nodex.tests.addons.redis;

import org.nodex.java.addons.redis.RedisReply;
import org.nodex.java.addons.redis.ReplyParser;
import org.nodex.java.core.Handler;
import org.nodex.java.core.buffer.Buffer;
import org.nodex.tests.Utils;
import org.nodex.tests.core.TestBase;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * @author <a href="http://tfox.org">Tim Fox</a>
 */
public class ReplyParserTest extends TestBase {

  @Test
  public void testParser() throws Exception {

    Responses resp = new Responses();
    int numResponses = 1000;
    Random random = new Random();

    for (int i = 0; i < numResponses; i++) {
      int rand = random.nextInt(5);

      switch (rand) {
        case 0:
          resp.addOneLine(Utils.randomAlphaString(100));
          break;
        case 1:
          resp.addError(Utils.randomAlphaString(100));
          break;
        case 2:
          resp.addIntegerLine(random.nextInt(10000));
          break;
        case 3:
          resp.addBulk(Utils.generateRandomByteArray(100));
          break;
        case 4:
          byte[][] multi = new byte[1 + random.nextInt(10)][];
          for (int j = 0; j < multi.length; j++) {
            multi[j] = Utils.generateRandomByteArray(100);
          }
          resp.addMultiBulk(multi);
          break;
        default:
          throw new IllegalStateException("Invalid value");
      }
    }

    final List<RedisReply> completions = new ArrayList<>();
    ReplyParser parser = new ReplyParser(new Handler<RedisReply>() {
      public void handle(RedisReply compl) {
        completions.add(compl);
      }
    });
    parser.handle(resp.buff);
    resp.validate(completions);

    throwAssertions();
  }

  private static class Responses {

    Buffer buff = Buffer.create(0);

    List<Object> responses = new ArrayList<>();

    static byte[] CRLF = new byte[] {'\r', '\n'};

    void addError(String error) {
      buff.appendByte((byte)'-').appendString(error).appendBytes(CRLF);
      responses.add(error);
    }

    void addOneLine(String line) {
      buff.appendByte((byte)'+').appendString(line).appendBytes(CRLF);
      responses.add(line);
    }

    void addIntegerLine(int result) {
      buff.appendByte((byte)':').appendString(String.valueOf(result)).appendBytes(CRLF);
      responses.add(result);
    }

    void addBulk(byte[] data) {
      appendBulk(data);
      responses.add(data);
    }

    void addMultiBulk(byte[][] data) {
      buff.appendByte((byte)'*').appendString(String.valueOf(data.length)).appendBytes(CRLF);
      for (int i = 0; i < data.length; i++) {
        byte[] bulk = data[i];
        appendBulk(bulk);
      }
      responses.add(data);
    }

    void appendBulk(byte[] data) {
      buff.appendByte((byte)'$').appendString(String.valueOf(data.length)).appendBytes(CRLF);
      buff.appendBytes(data).appendBytes(CRLF);
    }

    void validate(List<RedisReply> completions) {
      azzert(completions.size() == responses.size());

      Iterator<Object> respIter = responses.iterator();
      for (RedisReply compl: completions) {
        Object resp = respIter.next();
        if (compl.error != null) {
          azzert(resp.equals(compl.error));
        } else {
          if (resp instanceof String) {
            azzert(compl.line.equals(resp));
          } else if (resp instanceof Integer) {
            azzert(resp.equals(compl.intResult));
          } else if (resp instanceof Buffer) {
            azzert(Utils.buffersEqual(compl.bulkResult, (Buffer)resp));
          } else if (resp instanceof Buffer[]) {
            Buffer[] expected = (Buffer[])resp;
            Buffer[] actual = compl.multiBulkResult;
            azzert(expected.length == actual.length);
            for (int i = 0; i < expected.length; i++) {
              azzert(Utils.buffersEqual(expected[i], actual[i]));
            }
          }
        }
      }
    }
  }
}
