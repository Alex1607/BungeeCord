package net.md_5.bungee.protocol;

import com.google.common.base.Charsets;
import com.google.gson.JsonSyntaxException;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import ru.leymooo.botfilter.utils.FastException;
import ru.leymooo.botfilter.utils.FastOverflowPacketException;

@RequiredArgsConstructor
public abstract class DefinedPacket
{

    private static final FastException VARINT_TOO_BIG = new FastException( "varint too big" ); //BotFilter
    private static final FastException ILLEGAL_BUF = new FastException( "Buffer is no longer readable" ); //BotFilter

    public static void writeString(String s, ByteBuf buf)
    {
        if ( s.length() > Short.MAX_VALUE )
        {
            throw new OverflowPacketException( String.format( "Cannot send string longer than Short.MAX_VALUE (got %s characters)", s.length() ) );
        }

        byte[] b = s.getBytes( Charsets.UTF_8 );
        writeVarInt( b.length, buf );
        buf.writeBytes( b );
    }

    public static String readString(ByteBuf buf)
    {
        int len = readVarInt( buf );
        if ( len > Short.MAX_VALUE )
        {
            throw new FastOverflowPacketException( String.format( "Cannot receive string longer than Short.MAX_VALUE (got " + len + " characters)" ) );
        }

        byte[] b = new byte[ len ];
        buf.readBytes( b );

        return new String( b, Charsets.UTF_8 );
    }

    //BotFilter start
    public static boolean fix_scoreboards;
    public static void writeStringAsChatComponent(String s, ByteBuf buf)
    {
        if ( fix_scoreboards )
        {
            try
            {
                if ( !ComponentSerializer.getJSON_PARSER().parse( s ).isJsonPrimitive() )
                {
                    writeString( s, buf );  //Its a valid json string
                    return;
                }
            } catch ( JsonSyntaxException ex )
            {

            }
            writeString( ComponentSerializer.toString( TextComponent.fromLegacyText( s ) ), buf );
        } else
        {
            writeString( s, buf );
        }
    }

    public static String readChatComponentAsString(ByteBuf buf)
    {
        String json = readString( buf );
        if ( fix_scoreboards )
        {
            BaseComponent[] components = ComponentSerializer.parse( json );
            return ( components.length == 0 || components[0] == null ) ? json : TextComponent.toLegacyText( components );
        } else
        {
            return json;
        }
    }
    //BotFilter end

    public static void writeArray(byte[] b, ByteBuf buf)
    {
        if ( b.length > Short.MAX_VALUE )
        {
            throw new OverflowPacketException( String.format( "Cannot send byte array longer than Short.MAX_VALUE (got %s bytes)", b.length ) );
        }
        writeVarInt( b.length, buf );
        buf.writeBytes( b );
    }

    public static byte[] toArray(ByteBuf buf)
    {
        byte[] ret = new byte[ buf.readableBytes() ];
        buf.readBytes( ret );

        return ret;
    }

    public static byte[] readArray(ByteBuf buf)
    {
        return readArray( buf, buf.readableBytes() );
    }

    public static byte[] readArray(ByteBuf buf, int limit)
    {
        int len = readVarInt( buf );
        if ( len > limit )
        {
            throw new FastOverflowPacketException( String.format( "Cannot receive byte array longer than %s (got %s bytes)", limit, len ) );
        }
        byte[] ret = new byte[ len ];
        buf.readBytes( ret );
        return ret;
    }

    public static int[] readVarIntArray(ByteBuf buf)
    {
        int len = readVarInt( buf );
        int[] ret = new int[ len ];

        for ( int i = 0; i < len; i++ )
        {
            ret[i] = readVarInt( buf );
        }

        return ret;
    }

    public static void writeStringArray(List<String> s, ByteBuf buf)
    {
        writeVarInt( s.size(), buf );
        for ( String str : s )
        {
            writeString( str, buf );
        }
    }

    public static List<String> readStringArray(ByteBuf buf)
    {
        int len = readVarInt( buf );
        List<String> ret = new ArrayList<>( len );
        for ( int i = 0; i < len; i++ )
        {
            ret.add( readString( buf ) );
        }
        return ret;
    }

    public static int readVarInt(ByteBuf input)
    {
        return readVarInt( input, 5 );
    }

    public static int readVarInt(ByteBuf input, int maxBytes)
    {
        int out = 0;
        int bytes = 0;
        byte in;
        int readable = input.readableBytes(); //BotFilter
        while ( true )
        {
            //BotFilter start
            if ( readable-- == 0 )
            {
                throw ILLEGAL_BUF;
            }
            //BotFiter end
            in = input.readByte();

            out |= ( in & 0x7F ) << ( bytes++ * 7 );

            if ( bytes > maxBytes )
            {
                throw VARINT_TOO_BIG; //BotFilter
            }

            if ( ( in & 0x80 ) != 0x80 )
            {
                break;
            }
        }

        return out;
    }

    public static void writeVarInt(int value, ByteBuf output)
    {
        int part;
        while ( true )
        {
            part = value & 0x7F;

            value >>>= 7;
            if ( value != 0 )
            {
                part |= 0x80;
            }

            output.writeByte( part );

            if ( value == 0 )
            {
                break;
            }
        }
    }

    public static int readVarShort(ByteBuf buf)
    {
        int low = buf.readUnsignedShort();
        int high = 0;
        if ( ( low & 0x8000 ) != 0 )
        {
            low = low & 0x7FFF;
            high = buf.readUnsignedByte();
        }
        return ( ( high & 0xFF ) << 15 ) | low;
    }

    public static void writeVarShort(ByteBuf buf, int toWrite)
    {
        int low = toWrite & 0x7FFF;
        int high = ( toWrite & 0x7F8000 ) >> 15;
        if ( high != 0 )
        {
            low = low | 0x8000;
        }
        buf.writeShort( low );
        if ( high != 0 )
        {
            buf.writeByte( high );
        }
    }

    public static void writeUUID(UUID value, ByteBuf output)
    {
        output.writeLong( value.getMostSignificantBits() );
        output.writeLong( value.getLeastSignificantBits() );
    }

    public static UUID readUUID(ByteBuf input)
    {
        return new UUID( input.readLong(), input.readLong() );
    }

    public void read(ByteBuf buf)
    {
        throw new UnsupportedOperationException( "Packet must implement read method" );
    }

    public void read(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        read( buf );
    }

    public void write(ByteBuf buf)
    {
        throw new UnsupportedOperationException( "Packet must implement write method" );
    }

    public void write(ByteBuf buf, ProtocolConstants.Direction direction, int protocolVersion)
    {
        write( buf );
    }

    public abstract void handle(AbstractPacketHandler handler) throws Exception;

    @Override
    public abstract boolean equals(Object obj);

    @Override
    public abstract int hashCode();

    @Override
    public abstract String toString();
}
