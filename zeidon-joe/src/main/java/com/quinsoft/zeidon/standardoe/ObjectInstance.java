/**
    This file is part of the Zeidon Java Object Engine (Zeidon JOE).

    Zeidon JOE is free software: you can redistribute it and/or modify
    it under the terms of the GNU Lesser General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Zeidon JOE is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Lesser General Public License for more details.

    You should have received a copy of the GNU Lesser General Public License
    along with Zeidon JOE.  If not, see <http://www.gnu.org/licenses/>.

    Copyright 2009-2015 QuinSoft
 */
/**
 *
 */
package com.quinsoft.zeidon.standardoe;

import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.quinsoft.zeidon.ActivateOptions;
import com.quinsoft.zeidon.ZeidonException;
import com.quinsoft.zeidon.objectdefinition.LodDef;

class ObjectInstance
{
    private TaskImpl            task;
    private final LodDef        lodDef;

    /**
     * A unique internal ID created for each OI.  Note this ID is unique only within
     * a single JOE instance.  Use uuid for IDs that are unique across instances.
     */
    private final long          id;

    private final UUID          uuid;

    private EntityInstanceImpl  rootEntityInstance;
    private boolean             isLocked;
    private boolean             isReadOnly;
    private final AtomicInteger versionedInstances;

    /**
     * If true then this OI has been updated since it was last loaded from the DB.
     */
    private boolean         updated = false;

    /**
     * If true then this OI has been updated since it was last loaded from a file.
     */
    private boolean         updatedFile = false;

    /**
     * Stores the options used when activating the OI.  This is intended to be used by lazy load
     * and pagination processing.
     */
    private ActivateOptions activateOptions;

    // Following used for commit processing
    boolean dbhNeedsForeignKeys;
    boolean dbhNeedsGenKeys;

    /**
     * If true, then tell cursor processing to not attempt to lazy load entities.
     * This is intended to be used during activation so we don't go through
     * unnecessary processing.
     */
    private boolean ignoreLazyLoadEntities = false;

    /**
     * This keeps track of attribute hash keys that are global to the OI.  Intended for use
     * by cursor.setFirst() processing.
     */
    private AttributeHashKeyMap attributeHashkeyMap;

    /**
     * This is a weak map of the ViewCursors that refer to this ObjectInstance.
     * Note: This is currently only used if the LodDef has physical mappings; it
     * is intended to be used by merge processing for commits that are made on
     * remote servers.
     */
    //private final ConcurrentMap<ViewCursor, Boolean> referringViewCursors;

    /**
     * This is the total count of root entities.  For OIs loaded with paging this
     * is the total number of roots, not just the ones loaded.
     */
    private Integer totalRootCount;

    ObjectInstance(TaskImpl task, LodDef lodDef)
    {
        this.task = task;
        this.lodDef = lodDef;
        id = task.getObjectEngine().getNextObjectKey();
        uuid = task.getObjectEngine().generateUuid();
        versionedInstances = new AtomicInteger( 0 );
    }

    LodDef getLodDef()
    {
        return lodDef;
    }

    TaskImpl getTask()
    {
        return task;
    }

    /**
     * A unique internal ID created for each OI.  Note this ID is unique only within
     * a single JOE instance.  Use uuid for IDs that are unique across instances.
     */
    long getId()
    {
        return id;
    }

    EntityInstanceImpl getRootEntityInstance()
    {
        return rootEntityInstance;
    }

    EntityInstanceImpl getLastEntityInstance()
    {
        if ( rootEntityInstance == null )
            return null;

        return rootEntityInstance.getLastTwin().getLastChildHier();
    }


    void setRootEntityInstance(EntityInstanceImpl rootEntityInstance)
    {
        this.rootEntityInstance = rootEntityInstance;
    }

    boolean isUpdated()
    {
        return updated;
    }

    void setUpdated(boolean updated)
    {
        this.updated = updated;
        if ( updated )
            setUpdatedFile( true );
    }

    boolean isUpdatedFile()
    {
        return updatedFile;
    }

    void setUpdatedFile(boolean updatedFile)
    {
        this.updatedFile = updatedFile;
    }

    boolean isLocked()
    {
        return isLocked;
    }

    void setLocked(boolean isLocked)
    {
        this.isLocked = isLocked;
    }

    boolean isReadOnly()
    {
        return isReadOnly;
    }

    void setReadOnly(boolean isReadOnly)
    {
        this.isReadOnly = isReadOnly;
    }

    void incrementVersionedCount()
    {
        versionedInstances.incrementAndGet();
    }

    void decrementVersionedCount()
    {
        versionedInstances.decrementAndGet();
    }

    boolean isVersioned()
    {
        return versionedInstances.intValue() > 0;
    }

    EntityInstanceImpl findByHierIndex( long index )
    {
        return getRootEntityInstance().findByHierIndex( index );
    }

    /**
     * Iterable that loops through all entities, including hidden ones.
     *
     * @return
     */
    Iterable<EntityInstanceImpl> getEntities()
    {
        return getEntities( true );
    }

    Iterable<EntityInstanceImpl> getEntities( final boolean allowHidden )
    {
        return new Iterable<EntityInstanceImpl>()
        {
            @Override
            public Iterator<EntityInstanceImpl> iterator()
            {
                return new IteratorBuilder(ObjectInstance.this)
                                .withOiScoping( ObjectInstance.this )
                                .allowHidden( allowHidden )
                                .setLazyLoad( false )
                                .build();
            }
        };
    }

    @Override
    public String toString()
    {
        return lodDef.toString();
    }

    /**
     * Goes through all the prev/next pointers and attempts to verify that they are correctly
     * set.
     *
     * @return
     */
    boolean validateChains()
    {
        EntityInstanceImpl root = getRootEntityInstance();
        if ( root == null )
            return true;

        root = root.getLatestVersion();
        for ( EntityInstanceImpl scan = root; scan != null; scan = scan.getNextHier() )
        {
            if ( scan.getObjectInstance() != this )
            {
                writeValidateError( scan, scan, "EI has mis-matching OI" );
                return false;
            }

            if ( scan.getPrevHier() != null )
            {
                if ( scan.getPrevHier().getNextHier() != scan )
                {
                    writeValidateError( scan, scan.getPrevHier(), "Prev/Next hier pointers don't match" );
                    return false;
                }
            }

            EntityInstanceImpl next = scan.getNextHier();
            if ( next != null )
            {
                if ( next.getPrevHier() != scan )
                {
                    writeValidateError( scan, next, "Next/prev hier pointers don't match" );
                    scan.logEntity( false );
                    next.logEntity( false );
                    return false;
                }
            }

            if ( scan.getNextTwin() != null )
            {
                if ( scan.getNextTwin().getPrevTwin() != scan )
                {
                    writeValidateError( scan, scan.getNextTwin(), "Next/prev Twin pointers don't match" );
                    return false;
                }

                if ( scan.getNextTwin().getEntityDef() != scan.getEntityDef() )
                {
                    writeValidateError( scan, scan.getNextTwin(), "EntityDef next Twin pointers don't match" );
                    return false;
                }
            }

            if ( scan.getPrevTwin() != null )
            {
                if ( scan.getPrevTwin().getParent() != scan.getParent() )
                {
                    writeValidateError( scan, scan.getPrevTwin(), "Parent pointers don't match" );
                    return false;
                }

                if ( scan.getPrevTwin().getNextTwin() != scan )
                {
                    writeValidateError( scan, scan.getPrevTwin(), "Prev/Next twin pointers don't match" );
                    return false;
                }

                if ( scan.getPrevTwin().getEntityDef() != scan.getEntityDef() )
                {
                    writeValidateError( scan, scan.getPrevTwin(), "Prev twin EntityDef don't match" );
                    return false;
                }
            }
        }

        return true;
    }

    private void writeValidateError( EntityInstanceImpl scan, EntityInstanceImpl other, String msg )
    {
        getTask().log().error( msg + "\nScan = %s (%d)\nEI2  = %s (%d)",
                               scan, scan.getEntityKey(), other, other.getEntityKey() );
    }

    /**
     * @param task2
     */
    void setTask( TaskImpl task )
    {
        this.task = task;
    }

    synchronized AttributeHashKeyMap getAttributeHashkeyMap()
    {
        if ( attributeHashkeyMap == null )
            attributeHashkeyMap = new AttributeHashKeyMap( this );

        return attributeHashkeyMap;
    }

    /**
     * @return the activateOptions
     */
    ActivateOptions getActivateOptions()
    {
        return activateOptions;
    }

    /**
     * @param activateOptions the activateOptions to set
     */
    void setActivateOptions( ActivateOptions activateOptions )
    {
        this.activateOptions = activateOptions;
    }

    UUID getUuid()
    {
        return uuid;
    }

    boolean isIgnoreLazyLoadEntities()
    {
        return ignoreLazyLoadEntities;
    }

    void setIgnoreLazyLoadEntities( boolean ignoreLazyLoadEntities )
    {
        this.ignoreLazyLoadEntities = ignoreLazyLoadEntities;
    }

    /**
     * Creates a new View that references this OI.  If ei is not null
     * then set the cursor as well.
     *
     * @param ei
     * @return
     */
    ViewImpl createView( EntityInstanceImpl ei )
    {
        ViewImpl view = new ViewImpl( this );
        if ( ei != null )
            view.cursor( ei.getEntityDef() ).setCursor( ei );

        return view;
    }

    int getEntityCount( boolean includeHidden )
    {
        int count = 0;
        for ( EntityInstanceImpl ei = rootEntityInstance; ei != null; ei = ei.getNextHier() )
        {
            if ( includeHidden || ! ei.isHidden() )
                count++;
        }

        return count;
    }

    Integer getTotalRootCount()
    {
        return totalRootCount;
    }

    void setTotalRootCount( int totalRootCount )
    {
        if ( this.totalRootCount != null )
            throw new ZeidonException( "Total root count has already been set for this OI." );

        this.totalRootCount = totalRootCount;
    }
}
