/**
 *
 */
package com.quinsoft.zeidon.utils;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.EnumSet;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.quinsoft.zeidon.AttributeInstance;
import com.quinsoft.zeidon.EntityInstance;
import com.quinsoft.zeidon.SerializeOi;
import com.quinsoft.zeidon.StreamWriter;
import com.quinsoft.zeidon.View;
import com.quinsoft.zeidon.WriteOiFlags;
import com.quinsoft.zeidon.ZeidonException;
import com.quinsoft.zeidon.objectdefinition.ViewEntity;

/**
 * Serializes an OI by writing it as a JSON stream.  This does not write any Zeidon
 * information (e.g. incremental flags).  Zeidon can retrieve an OI written with this
 * object but it won't have any linking or incremental flags.
 *
 * @author dgc
 *
 */
public class WriteOisToJsonStreamNoIncrementals implements StreamWriter
{
    private final static String VERSION = "1";

    private Collection<? extends View> viewList;
    private Writer writer;
    private EnumSet<WriteOiFlags> flags;

    private JsonGenerator jg;
    private int rootCount;

    @Override
    public void writeToStream( SerializeOi options )
    {
        this.viewList = options.getViewList();
        this.writer = options.getWriter();
        if ( options.getFlags() == null )
            flags = EnumSet.noneOf( WriteOiFlags.class );
        else
            flags = options.getFlags();
        if ( flags.contains( WriteOiFlags.INCREMENTAL ) )
            throw new ZeidonException( "This JSON stream writer not intended for writing incremental." );

        JsonFactory jsonF = new JsonFactory();
        try
        {
            jg = jsonF.createGenerator( writer );
            jg.useDefaultPrettyPrinter(); // enable indentation just to make debug/testing easier
            jg.writeStartObject();

            jg.writeStringField( "version", VERSION );

            for ( View view : viewList )
                writeOi( view );

            jg.writeEndObject();
            jg.close();
        }
        catch ( Exception e )
        {
            throw ZeidonException.wrapException( e );
        }
    }

    private void writeOi( View view ) throws Exception
    {
        view = view.newView();  // To preserve cursor positions in the original view.
        ViewEntity lastViewEntity = null;

        ViewEntity rootViewEntity = view.getViewOd().getRoot();
        rootCount = view.cursor( rootViewEntity ).getEntityCount();
        if ( rootCount > 1 )
        {
            jg.writeArrayFieldStart( rootViewEntity.getName() );
            jg.writeStartObject();
        }
        else
            jg.writeObjectFieldStart( rootViewEntity.getName() );
        
        for ( EntityInstance ei:  view.cursor( rootViewEntity ).eachEntity() )
        {
            if ( ei.hasPrevTwin() )
                jg.writeStartObject();
            
            lastViewEntity = writeEntity( ei, lastViewEntity );
            
            if ( ei.hasNextTwin() )
                jg.writeEndObject();
        }

        jg.writeEndObject();
        if ( rootCount > 1 )
            jg.writeEndArray();
    }
    
    private ViewEntity writeEntity( EntityInstance ei, ViewEntity lastViewEntity ) throws Exception
    {
        try
        {
            // See if we need to open or close an array field.
            final ViewEntity viewEntity = ei.getViewEntity();

            for ( AttributeInstance attrib : ei.attributeList( false ) )
            {
                if ( attrib.getViewAttribute().isHidden() )
                    continue;
                
                String value = attrib.getString();
                jg.writeStringField( attrib.getViewAttribute().getName(), value );
            }

            // Loop through the children and add them.
            ViewEntity lastChildViewEntity = null;
            for ( EntityInstance child : ei.getDirectChildren() )
            {
                ViewEntity childViewEntity = child.getViewEntity();
                if ( ! child.hasPrevTwin() )
                {
                    if ( childViewEntity.getMaxCardinality() > 1 )
                    {
                        jg.writeArrayFieldStart( childViewEntity.getName() );
                        jg.writeStartObject();
                    }
                    else
                        jg.writeObjectFieldStart( viewEntity.getName() );
                }
                else
                    jg.writeStartObject();
                
                lastChildViewEntity = writeEntity( child, lastChildViewEntity );
                
                jg.writeEndObject();
                if ( ! child.hasNextTwin() )
                {
                    if ( childViewEntity.getMaxCardinality() > 1 )
                        jg.writeEndArray();
                }
            }

            return viewEntity;
        }

        catch ( Exception e )
        {
            throw ZeidonException.wrapException( e ).prependEntityInstance( ei );
        }
    }
}