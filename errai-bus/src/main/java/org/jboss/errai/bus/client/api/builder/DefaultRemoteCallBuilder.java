/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.bus.client.api.builder;

import org.jboss.errai.bus.client.api.ErrorCallback;
import org.jboss.errai.bus.client.api.Message;
import org.jboss.errai.bus.client.api.MessageCallback;
import org.jboss.errai.bus.client.api.RemoteCallback;
import org.jboss.errai.bus.client.api.base.DefaultErrorCallback;
import org.jboss.errai.bus.client.framework.MessageBus;
import org.jboss.errai.bus.client.framework.ProxyFactory;
import org.jboss.errai.bus.client.framework.RPCStub;
import org.jboss.errai.bus.client.framework.RemoteServiceProxyFactory;
import org.jboss.errai.common.client.framework.Assert;
import org.jboss.errai.common.client.protocols.MessageParts;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

/**
 * The <tt>DefaultRemoteCallBuilder</tt> facilitates the building of a remote call. Ensures that the remote call is
 * constructed properly
 */
public class DefaultRemoteCallBuilder {
  private static ProxyFactory proxyFactory = new RemoteServiceProxyFactory();

  /* Used to generate a unique number */
  private volatile static int callCounter = 0;

  private final Message message;
  private RemoteCallback<Object> remoteCallback;

  /* The type of response that is expected by the callback */
  private Class<Object> responseType = Object.class;

  public DefaultRemoteCallBuilder(Message message) {
    this.message = message;
  }

  public <T, R> T call(final RemoteCallback<R> callback, final Class<T> remoteService) {
    return call(callback, null, remoteService);
  }

  public <T, R> T call(final RemoteCallback<R> callback, final ErrorCallback errorCallback, final Class<T> remoteService) {
    T svc = proxyFactory.getRemoteProxy(remoteService);
    ((RPCStub) svc).setRemoteCallback(callback);
    ((RPCStub) svc).setErrorCallback(errorCallback);
    return svc;
  }

  /**
   * Only intended for use by generated code. Use {@link #call(RemoteCallback, Class)} or
   * {@link #call(RemoteCallback, ErrorCallback, Class)} from handwritten code.
   * <p>
   * Creates, implements and returns an instance of <tt>RemoteCallEndpointDef</tt> and all applicable arguments, which
   * should be instantiated after this call to <tt>serviceName</tt>. The endpoint allows a function from a service to be
   * called directly, rather than waiting for a response to a message.
   * 
   * @param serviceName
   *          the service to call, and create a remote call endpoint for
   * @return the remote call endpoint.
   */
  public RemoteCallEndpointDef call(final String serviceName) {
    message.toSubject(serviceName + ":RPC");

    final RemoteCallSendable sendable = new RemoteCallSendable() {

      @Override
      public void sendNowWith(final MessageBus bus) {
        Integer id = null;

        final String replyTo =
            message.getSubject() + "." + message.getCommandType() + ":RespondTo:" + (id = uniqueNumber());

        final String errorTo =
            message.getSubject() + "." + message.getCommandType() + ":Errors:" + ((id == null) ? uniqueNumber() : id);

        if (remoteCallback != null) {
          bus.subscribe(replyTo,
              new MessageCallback() {
                @Override
                public void callback(Message message) {
                  bus.unsubscribeAll(replyTo);
                  if (DefaultRemoteCallBuilder.this.message.getErrorCallback() != null) {
                    bus.unsubscribeAll(errorTo);
                  }
                  remoteCallback.callback(message.get(responseType, "MethodReply"));
                }
              }
          );
          message.set(MessageParts.ReplyTo, replyTo);
        }

        if (message.getErrorCallback() != null) {
          bus.subscribe(errorTo,
              new MessageCallback() {
                @Override
                public void callback(Message m) {
                  bus.unsubscribeAll(errorTo);
                  if (remoteCallback != null) {
                    bus.unsubscribeAll(replyTo);
                  }
                  message.getErrorCallback().error(message, m.get(Throwable.class, MessageParts.Throwable));
                }
              }
          );
          message.set(MessageParts.ErrorTo, errorTo);
        }

        message.sendNowWith(bus);
      }
    };

    final RemoteCallErrorDef errorDef = new RemoteCallErrorDef() {
      @Override
      public RemoteCallSendable errorsHandledBy(ErrorCallback errorCallback) {
        message.errorsCall(errorCallback);
        return sendable;
      }

      @Override
      public RemoteCallSendable defaultErrorHandling() {
        message.errorsCall(DefaultErrorCallback.INSTANCE);
        return sendable;
      }
    };

    final RemoteCallResponseDef respondDef = new RemoteCallResponseDef() {
      @Override
      @SuppressWarnings("unchecked")
      public <T> RemoteCallErrorDef respondTo(Class<T> returnType, RemoteCallback<T> callback) {
        responseType = (Class<Object>) returnType;
        remoteCallback = (RemoteCallback<Object>) callback;
        return errorDef;
      }
    };

    return new RemoteCallEndpointDef() {
      @Override
      public RemoteCallResponseDef endpoint(String endPointName) {
        message.command(endPointName);
        return respondDef;
      }

      @Override
      public RemoteCallResponseDef endpoint(String endPointName, Annotation[] qualifiers, Object[] args) {
        message.command(endPointName);

        if (qualifiers != null) {
          List<String> qualNames = new ArrayList<String>(qualifiers.length);
          for (Annotation a : qualifiers) {
            qualNames.add(a.annotationType().getName());
          }

          message.set("Qualifiers", qualNames);
        }
        if (args != null) message.set("MethodParms", args);

        return respondDef;
      }

      @Override
      public RemoteCallResponseDef endpoint(String endPointName, Object[] args) {
        message.command(endPointName);
        if (args != null)
          message.set("MethodParms", args);
        return respondDef;
      }
    };
  }

  private static int uniqueNumber() {
    return ++callCounter > 10000 ? callCounter = 0 : callCounter;
  }

  /**
   * Sets the proxy provider factory that is used by MessageBuilder and friends for creating remote proxies. Unless you
   * are creating an Errai extension that provides an alternative remoting mechanism, there is never a need to call this
   * method.
   * 
   * @param provider
   *          The ProxyProvider that provides RPC proxies to message builders. Not null.
   */
  public static void setProxyFactory(ProxyFactory provider) {
    proxyFactory = Assert.notNull(provider);
  }

  public static void destroyProxyFactory() {
    proxyFactory = null;
  }
}