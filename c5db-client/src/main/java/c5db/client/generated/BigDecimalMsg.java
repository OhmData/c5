// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from resources

package c5db.client.generated;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.dyuproject.protostuff.ByteString;import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.UninitializedMessageException;

public final class BigDecimalMsg implements Externalizable, Message<BigDecimalMsg>, Schema<BigDecimalMsg>
{

    public static Schema<BigDecimalMsg> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static BigDecimalMsg getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final BigDecimalMsg DEFAULT_INSTANCE = new BigDecimalMsg();


    private ByteString bigdecimalMsg;

    public BigDecimalMsg()
    {

    }

    public BigDecimalMsg(
        ByteString bigdecimalMsg
    )
    {
        this.bigdecimalMsg = bigdecimalMsg;
    }

    @Override
    public String toString() {
        return "BigDecimalMsg{" +
                    "bigdecimalMsg=" + bigdecimalMsg +
                '}';
    }
    // getters and setters

    // bigdecimalMsg

    public ByteString getBigdecimalMsg()
    {
        return bigdecimalMsg;
    }


    public BigDecimalMsg setBigdecimalMsg(ByteString bigdecimalMsg)
    {
        this.bigdecimalMsg = bigdecimalMsg;
        return this;
    }

    // java serialization

    public void readExternal(ObjectInput in) throws IOException
    {
        GraphIOUtil.mergeDelimitedFrom(in, this, this);
    }

    public void writeExternal(ObjectOutput out) throws IOException
    {
        GraphIOUtil.writeDelimitedTo(out, this, this);
    }

    // message method

    public Schema<BigDecimalMsg> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public BigDecimalMsg newMessage()
    {
        return new BigDecimalMsg();
    }

    public Class<BigDecimalMsg> typeClass()
    {
        return BigDecimalMsg.class;
    }

    public String messageName()
    {
        return BigDecimalMsg.class.getSimpleName();
    }

    public String messageFullName()
    {
        return BigDecimalMsg.class.getName();
    }

    public boolean isInitialized(BigDecimalMsg message)
    {
        return
            message.bigdecimalMsg != null;
    }

    public void mergeFrom(Input input, BigDecimalMsg message) throws IOException
    {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    message.bigdecimalMsg = input.readBytes();
                    break;
                default:
                    input.handleUnknownField(number, this);
            }
        }
    }


    public void writeTo(Output output, BigDecimalMsg message) throws IOException
    {
        if(message.bigdecimalMsg == null)
            throw new UninitializedMessageException(message);
        output.writeBytes(1, message.bigdecimalMsg, false);
    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "bigdecimalMsg";
            default: return null;
        }
    }

    public int getFieldNumber(String name)
    {
        final Integer number = __fieldMap.get(name);
        return number == null ? 0 : number.intValue();
    }

    private static final java.util.HashMap<String,Integer> __fieldMap = new java.util.HashMap<String,Integer>();
    static
    {
        __fieldMap.put("bigdecimalMsg", 1);
    }

}
