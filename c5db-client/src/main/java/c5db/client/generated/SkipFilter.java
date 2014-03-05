// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from resources

package c5db.client.generated;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;
import com.dyuproject.protostuff.UninitializedMessageException;

public final class SkipFilter implements Externalizable, Message<SkipFilter>, Schema<SkipFilter>
{

    public static Schema<SkipFilter> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static SkipFilter getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final SkipFilter DEFAULT_INSTANCE = new SkipFilter();


    private Filter filter;

    public SkipFilter()
    {

    }

    public SkipFilter(
        Filter filter
    )
    {
        this.filter = filter;
    }

    @Override
    public String toString() {
        return "SkipFilter{" +
                    "filter=" + filter +
                '}';
    }
    // getters and setters

    // filter

    public Filter getFilter()
    {
        return filter;
    }


    public SkipFilter setFilter(Filter filter)
    {
        this.filter = filter;
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

    public Schema<SkipFilter> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public SkipFilter newMessage()
    {
        return new SkipFilter();
    }

    public Class<SkipFilter> typeClass()
    {
        return SkipFilter.class;
    }

    public String messageName()
    {
        return SkipFilter.class.getSimpleName();
    }

    public String messageFullName()
    {
        return SkipFilter.class.getName();
    }

    public boolean isInitialized(SkipFilter message)
    {
        return
            message.filter != null;
    }

    public void mergeFrom(Input input, SkipFilter message) throws IOException
    {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    message.filter = input.mergeObject(message.filter, Filter.getSchema());
                    break;

                default:
                    input.handleUnknownField(number, this);
            }
        }
    }


    public void writeTo(Output output, SkipFilter message) throws IOException
    {
        if(message.filter == null)
            throw new UninitializedMessageException(message);
        output.writeObject(1, message.filter, Filter.getSchema(), false);

    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "filter";
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
        __fieldMap.put("filter", 1);
    }

}
