// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from resources

package c5db.client.generated;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;

public final class RegionActionResult implements Externalizable, Message<RegionActionResult>, Schema<RegionActionResult>
{

    public static Schema<RegionActionResult> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static RegionActionResult getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final RegionActionResult DEFAULT_INSTANCE = new RegionActionResult();


    private List<ResultOrException> resultOrException;
    private NameBytesPair exception;

    public RegionActionResult()
    {

    }

    @Override
    public String toString() {
        return "RegionActionResult{" +
                    "resultOrException=" + resultOrException +
                    ", exception=" + exception +
                '}';
    }
    // getters and setters

    // resultOrException

    public List<ResultOrException> getResultOrExceptionList()
    {
        return resultOrException;
    }


    public RegionActionResult setResultOrExceptionList(List<ResultOrException> resultOrException)
    {
        this.resultOrException = resultOrException;
        return this;
    }

    // exception

    public NameBytesPair getException()
    {
        return exception;
    }


    public RegionActionResult setException(NameBytesPair exception)
    {
        this.exception = exception;
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

    public Schema<RegionActionResult> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public RegionActionResult newMessage()
    {
        return new RegionActionResult();
    }

    public Class<RegionActionResult> typeClass()
    {
        return RegionActionResult.class;
    }

    public String messageName()
    {
        return RegionActionResult.class.getSimpleName();
    }

    public String messageFullName()
    {
        return RegionActionResult.class.getName();
    }

    public boolean isInitialized(RegionActionResult message)
    {
        return true;
    }

    public void mergeFrom(Input input, RegionActionResult message) throws IOException
    {
        try {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    if(message.resultOrException == null)
                        message.resultOrException = new ArrayList<ResultOrException>();
                    message.resultOrException.add(input.mergeObject(null, ResultOrException.getSchema()));
                    break;

                case 2:
                    message.exception = input.mergeObject(message.exception, NameBytesPair.getSchema());
                    break;

                default:
                    input.handleUnknownField(number, this);
            }
        }
        } finally {
        if (message.resultOrException != null)
            message.resultOrException = java.util.Collections.unmodifiableList(message.resultOrException);
        else
            message.resultOrException = java.util.Collections.emptyList();
        }
    }


    public void writeTo(Output output, RegionActionResult message) throws IOException
    {
        if(message.resultOrException != null)
        {
            for(ResultOrException resultOrException : message.resultOrException)
            {
                if(resultOrException != null)
                    output.writeObject(1, resultOrException, ResultOrException.getSchema(), true);
            }
        }


        if(message.exception != null)
             output.writeObject(2, message.exception, NameBytesPair.getSchema(), false);

    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "resultOrException";
            case 2: return "exception";
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
        __fieldMap.put("resultOrException", 1);
        __fieldMap.put("exception", 2);
    }

}
