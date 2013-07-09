package ohmdb.flease;

import java.util.UUID;

/**
 * A value holder class for leases. Serializes to Flease.Lease protobuf.
 *
 * Comparable only checks the leaseExpiry, the 'datum' is not part of that.
 * Equality and hashcode based on value equality of both fields.
 */
public class LeaseValue implements Comparable<LeaseValue> {

    public final String datum;
    // If leaseExpiry = 0, then this is the "null" lease
    public final long leaseExpiry;
    // Will be null if there is no lease (ie: leaseExpiry = 0)
    public final UUID leaseOwner;

    private final Flease.Lease protobuf;

    public LeaseValue(final String datum, final long leaseExpiry, final UUID leaseOwner) {
        this.datum = datum;
        this.leaseExpiry = leaseExpiry;
        this.leaseOwner = leaseOwner;

        assert (leaseExpiry > 0 && leaseOwner != null) ||
                (leaseExpiry == 0 && leaseOwner == null);

        this.protobuf = null;
    }

    public LeaseValue() {
        this("", 0, null);
    }

    public LeaseValue(Flease.Lease fromMessage) {
        this.datum = fromMessage.getDatum();
        this.leaseExpiry = fromMessage.getLeaseExpiry();
        if (fromMessage.hasLeaseOwner())
            this.leaseOwner = UUID.fromString(fromMessage.getLeaseOwner());
        else
            this.leaseOwner = null;

        assert (leaseExpiry > 0 && leaseOwner != null) ||
                (leaseExpiry == 0 && leaseOwner == null);

        this.protobuf = fromMessage;
    }

    public boolean isEmpty() {
        return leaseExpiry == 0;
    }

    public boolean isBefore(long aTime) {
        if (leaseExpiry < aTime) {
            return true;
        }
        return false;
    }

    public Flease.Lease getMessage() {
        if (protobuf != null) return protobuf;

        Flease.Lease.Builder builder = Flease.Lease.newBuilder();

        builder.setDatum(datum)
                .setLeaseExpiry(leaseExpiry);

        if (leaseOwner != null) {
            builder.setLeaseOwner(leaseOwner.toString());
        }

        return builder.build();
    }

    @Override
    public String toString() {
        if (leaseExpiry == 0) return "<empty lease>";

        return datum + " (exp: " + leaseExpiry + ") owned by: " + leaseOwner;
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null) return false;

        if (other instanceof LeaseValue) {
            LeaseValue vOther = (LeaseValue)other;

            if (datum.equals(vOther.datum) &&
                    leaseExpiry == vOther.leaseExpiry &&
                    leaseOwner == vOther.leaseOwner)
                return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return ((int)(leaseExpiry ^ (leaseExpiry >>> 32)))
                ^
                datum.hashCode();
    }

    @Override
    public int compareTo(LeaseValue o) {
        if (o == null) {
            return 1;
        }

        if (leaseExpiry == o.leaseExpiry)
            return 0;
        else if (leaseExpiry > o.leaseExpiry)
            return 1;
        else
            return -1;
    }
}
