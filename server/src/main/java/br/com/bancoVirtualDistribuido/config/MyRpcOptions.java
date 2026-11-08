package br.com.bancoVirtualDistribuido.config;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.blocks.RequestOptions;
import org.jgroups.blocks.ResponseMode;
import org.jgroups.blocks.RspFilter;

public final class MyRpcOptions {

  private MyRpcOptions() {
  }

  // getMajority
  public static RequestOptions getMajority(JChannel channel) {
    int majority = Constants.getMajority(channel);

    RequestOptions options = new RequestOptions();
    options.setMode(ResponseMode.GET_ALL).setTimeout(Constants.timeOut);

    options.setRspFilter(new RspFilter() {
      int respostasRecebidas = 0;

      @Override
      public boolean isAcceptable(Object response, Address sender) {
        respostasRecebidas++;
        return true;
      }

      @Override
      public boolean needMoreResponses() {
        return respostasRecebidas < majority;
      }
    });

    return options;
  }

  public static RequestOptions getAll() {
    return new RequestOptions().setMode(ResponseMode.GET_ALL);
  }

  public static RequestOptions getFirst() {
    return new RequestOptions().setMode(ResponseMode.GET_FIRST);
  }

  public static RequestOptions getNone() {
    return new RequestOptions().setMode(ResponseMode.GET_NONE);
  }
}
