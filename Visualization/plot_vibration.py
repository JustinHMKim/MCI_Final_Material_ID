from matplotlib import axes
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np

filename_transmitter = "transmitter_data.csv"
filename_receiver = "receiver_data.csv"


def plot(filename):
    df = pd.read_csv(
        filename, names=["ranging Tstart", "acc timestamp", "acc z", "signal received"]
    )

    colors = ["blue", "red"]
    legends = ["false", "true"]
    grouped = df.groupby("signal received")
    for key, group in grouped:
        group.plot(
            ax=plt.gca(), kind="line", x="acc timestamp", y="acc z", color=colors[key]
        )


if __name__ == "__main__":
    plt.subplot(1, 2, 1)
    plot(filename_transmitter)
    plt.subplot(1, 2, 2)
    plot(filename_receiver)
    plt.show()
