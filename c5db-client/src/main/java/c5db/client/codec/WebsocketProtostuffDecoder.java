/*
 * Copyright (C) 2014  Ohm Data
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation, either version 3 of the
 *  License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package c5db.client.codec;

import c5db.client.generated.ClientProtos;
import com.google.common.util.concurrent.SettableFuture;
import com.google.protobuf.ZeroCopyLiteralByteString;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketClientHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketClientProtocolHandler;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.mortbay.log.Log;

import java.net.URISyntaxException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class WebsocketProtostuffDecoder extends WebSocketClientProtocolHandler {

  private static final long HANDSHAKE_TIMEOUT = 4000;
  private final WebSocketClientHandshaker handShaker;
  SettableFuture handshakeFuture = SettableFuture.create();

  public WebsocketProtostuffDecoder(WebSocketClientHandshaker handShaker) throws URISyntaxException {
    super(handShaker);
    this.handShaker = handShaker;
  }

  @Override
  public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
    if (evt instanceof ClientHandshakeStateEvent) {
      ClientHandshakeStateEvent clientHandshakeStateEvent = (ClientHandshakeStateEvent) evt;
      if (evt.equals(ClientHandshakeStateEvent.HANDSHAKE_COMPLETE)){
        handshakeFuture.set(true);
      }
    }
    super.userEventTriggered(ctx, evt);
  }


  @Override
  protected void decode(ChannelHandlerContext ctx, WebSocketFrame frame, List<Object> out) throws Exception {
    if (frame instanceof BinaryWebSocketFrame) {
      out.add(ClientProtos.Response.parseFrom(
          ZeroCopyLiteralByteString.copyFrom(frame.content().nioBuffer()))
      );
      super.decode(ctx, frame, out);
    }
  }

  public void syncOnHandshake() throws InterruptedException, ExecutionException, TimeoutException {
    while (!this.handShaker.isHandshakeComplete()) {
      handshakeFuture.get(HANDSHAKE_TIMEOUT, TimeUnit.MILLISECONDS);
    }
  }
}