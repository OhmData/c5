// Generated by http://code.google.com/p/protostuff/ ... DO NOT EDIT!
// Generated from resources

package c5db.client.generated;

import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.ArrayList;
import java.util.List;

import com.dyuproject.protostuff.ByteString;import com.dyuproject.protostuff.GraphIOUtil;
import com.dyuproject.protostuff.Input;
import com.dyuproject.protostuff.Message;
import com.dyuproject.protostuff.Output;
import com.dyuproject.protostuff.Schema;

public final class FirstKeyValueMatchingQualifiersFilter implements Externalizable, Message<FirstKeyValueMatchingQualifiersFilter>, Schema<FirstKeyValueMatchingQualifiersFilter>
{

    public static Schema<FirstKeyValueMatchingQualifiersFilter> getSchema()
    {
        return DEFAULT_INSTANCE;
    }

    public static FirstKeyValueMatchingQualifiersFilter getDefaultInstance()
    {
        return DEFAULT_INSTANCE;
    }

    static final FirstKeyValueMatchingQualifiersFilter DEFAULT_INSTANCE = new FirstKeyValueMatchingQualifiersFilter();


    private List<ByteString> qualifiers;

    public FirstKeyValueMatchingQualifiersFilter()
    {

    }

    @Override
    public String toString() {
        return "FirstKeyValueMatchingQualifiersFilter{" +
                    "qualifiers=" + qualifiers +
                '}';
    }
    // getters and setters

    // qualifiers

    public List<ByteString> getQualifiersList()
    {
        return qualifiers;
    }


    public FirstKeyValueMatchingQualifiersFilter setQualifiersList(List<ByteString> qualifiers)
    {
        this.qualifiers = qualifiers;
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

    public Schema<FirstKeyValueMatchingQualifiersFilter> cachedSchema()
    {
        return DEFAULT_INSTANCE;
    }

    // schema methods

    public FirstKeyValueMatchingQualifiersFilter newMessage()
    {
        return new FirstKeyValueMatchingQualifiersFilter();
    }

    public Class<FirstKeyValueMatchingQualifiersFilter> typeClass()
    {
        return FirstKeyValueMatchingQualifiersFilter.class;
    }

    public String messageName()
    {
        return FirstKeyValueMatchingQualifiersFilter.class.getSimpleName();
    }

    public String messageFullName()
    {
        return FirstKeyValueMatchingQualifiersFilter.class.getName();
    }

    public boolean isInitialized(FirstKeyValueMatchingQualifiersFilter message)
    {
        return true;
    }

    public void mergeFrom(Input input, FirstKeyValueMatchingQualifiersFilter message) throws IOException
    {
        try {
        for(int number = input.readFieldNumber(this);; number = input.readFieldNumber(this))
        {
            switch(number)
            {
                case 0:
                    return;
                case 1:
                    if(message.qualifiers == null)
                        message.qualifiers = new ArrayList<ByteString>();
                    message.qualifiers.add(input.readBytes());break;
                default:
                    input.handleUnknownField(number, this);
            }
        }
        } finally {
        if (message.qualifiers != null)
            message.qualifiers = java.util.Collections.unmodifiableList(message.qualifiers);
        else
            message.qualifiers = java.util.Collections.emptyList();
        }
    }


    public void writeTo(Output output, FirstKeyValueMatchingQualifiersFilter message) throws IOException
    {
        if(message.qualifiers != null)
        {
            for(ByteString qualifiers : message.qualifiers)
            {
                if(qualifiers != null)
                    output.writeBytes(1, qualifiers, true);
            }
        }
    }

    public String getFieldName(int number)
    {
        switch(number)
        {
            case 1: return "qualifiers";
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
        __fieldMap.put("qualifiers", 1);
    }

}
